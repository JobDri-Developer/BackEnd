#!/usr/bin/env python3
"""Sync corpus embeddings from Postgres to pgvector tables via Cohere."""

from __future__ import annotations

import argparse
import os
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import requests

try:
    import psycopg
except ImportError:  # pragma: no cover - fallback for older environments
    psycopg = None

try:
    import psycopg2
    from psycopg2.extras import RealDictCursor
except ImportError:  # pragma: no cover - optional fallback
    psycopg2 = None
    RealDictCursor = None


JOB_POSTING_SELECT_SQL = """
select id, embedding_text
from mock_job_posting_corpus
where is_valid_for_embedding = true
  and embedding_text is not null
order by id asc
"""

QUESTION_SELECT_SQL = """
select id, embedding_text
from mock_question_corpus
where is_valid_for_embedding = true
  and embedding_text is not null
order by id asc
"""

UPSERT_JOB_POSTING_SQL = """
insert into mock_job_posting_embeddings (corpus_id, embedding_model, embedding, created_at, updated_at)
values (%s, %s, %s::vector, now(), now())
on conflict (corpus_id) do update
set embedding_model = excluded.embedding_model,
    embedding = excluded.embedding,
    updated_at = now()
"""

UPSERT_QUESTION_SQL = """
insert into mock_question_embeddings (corpus_id, embedding_model, embedding, created_at, updated_at)
values (%s, %s, %s::vector, now(), now())
on conflict (corpus_id) do update
set embedding_model = excluded.embedding_model,
    embedding = excluded.embedding,
    updated_at = now()
"""


@dataclass
class SyncStats:
    job_posting_embeddings_upserted: int = 0
    question_embeddings_upserted: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Sync corpus embeddings into pgvector tables.")
    parser.add_argument("--env-file", default=".env", help="Path to env file")
    parser.add_argument("--limit", type=int, default=None, help="Limit rows per corpus type")
    parser.add_argument("--batch-size", type=int, default=None, help="Batch size for Cohere embed requests")
    parser.add_argument("--job-only", action="store_true", help="Sync only job posting corpus embeddings")
    parser.add_argument("--question-only", action="store_true", help="Sync only question corpus embeddings")
    return parser.parse_args()


def load_env_file(env_path: Path) -> None:
    if not env_path.exists():
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        os.environ.setdefault(key.strip(), value.strip())


def jdbc_to_postgres_dsn(jdbc_url: str) -> str:
    return jdbc_url[len("jdbc:") :] if jdbc_url.startswith("jdbc:") else jdbc_url


def connect():
    db_url = os.environ.get("DB_URL")
    db_user = os.environ.get("DB_USERNAME")
    db_password = os.environ.get("DB_PASSWORD")

    if not db_url or not db_user:
        raise SystemExit("DB_URL and DB_USERNAME must be set in environment or env file.")

    dsn = jdbc_to_postgres_dsn(db_url)
    if psycopg is not None:
        return psycopg.connect(dsn, user=db_user, password=db_password)
    if psycopg2 is not None:
        return psycopg2.connect(dsn, user=db_user, password=db_password, cursor_factory=RealDictCursor)
    raise SystemExit("Install psycopg or psycopg2-binary before running this script.")


def fetch_all(cur, query: str, limit: int | None) -> list[dict[str, Any]]:
    effective_query = query
    params: tuple[Any, ...] = ()
    if limit is not None:
        effective_query += "\nlimit %s"
        params = (limit,)

    cur.execute(effective_query, params)
    rows = cur.fetchall()
    if not rows:
        return []

    normalized_rows = []
    for row in rows:
        if isinstance(row, dict):
            normalized_rows.append(row)
            continue
        if hasattr(row, "_mapping"):
            normalized_rows.append(dict(row._mapping))
            continue
        desc = cur.description
        normalized_rows.append(
            {desc[i].name if hasattr(desc[i], "name") else desc[i][0]: row[i] for i in range(len(row))}
        )
    return normalized_rows


def chunked(items: list[Any], size: int) -> list[list[Any]]:
    actual_size = max(1, size)
    return [items[i : i + actual_size] for i in range(0, len(items), actual_size)]


def create_requests_session(cohere_api_key: str) -> requests.Session:
    session = requests.Session()
    session.headers.update(
        {
            "Authorization": f"Bearer {cohere_api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
    )
    return session


def embed_documents(session: requests.Session, texts: list[str], model: str, output_dimension: int) -> list[list[float]]:
    last_error = None
    for attempt in range(5):
        response = session.post(
            "https://api.cohere.com/v2/embed",
            json={
                "texts": texts,
                "model": model,
                "input_type": "search_document",
                "output_dimension": output_dimension,
                "embedding_types": ["float"],
            },
            timeout=(5, 60),
        )

        if response.status_code != 429:
            response.raise_for_status()
            data = response.json()
            embeddings = data.get("embeddings", {}).get("float")
            if not isinstance(embeddings, list):
                raise RuntimeError(f"Unexpected Cohere response: {data}")
            return embeddings

        retry_after = response.headers.get("Retry-After")
        sleep_seconds = float(retry_after) if retry_after else min(60, 2 ** (attempt + 1))
        print(
            f"Cohere rate limit hit for batch of {len(texts)} texts. "
            f"Retrying in {sleep_seconds:.1f}s... (attempt {attempt + 1}/5)",
            flush=True,
        )
        last_error = response
        time.sleep(sleep_seconds)

    if last_error is not None:
        last_error.raise_for_status()
    raise RuntimeError("Failed to get embedding response from Cohere.")


def vector_literal(values: list[float]) -> str:
    return "[" + ",".join(f"{value:.8f}" for value in values) + "]"


def upsert_embeddings(cur, sql: str, corpus_ids: list[int], embeddings: list[list[float]], model: str) -> int:
    if len(corpus_ids) != len(embeddings):
        raise RuntimeError("Embedding count does not match corpus row count.")

    for corpus_id, embedding in zip(corpus_ids, embeddings):
        cur.execute(sql, (corpus_id, model, vector_literal(embedding)))
    return len(corpus_ids)


def sync_dataset(cur, session: requests.Session, select_sql: str, upsert_sql: str, limit: int | None, batch_size: int, model: str, output_dimension: int) -> int:
    rows = fetch_all(cur, select_sql, limit)
    if not rows:
        return 0

    processed = 0
    for batch in chunked(rows, batch_size):
        corpus_ids = [int(row["id"]) for row in batch]
        texts = [str(row["embedding_text"]) for row in batch]
        embeddings = embed_documents(session, texts, model, output_dimension)
        processed += upsert_embeddings(cur, upsert_sql, corpus_ids, embeddings, model)
    return processed


def main() -> int:
    args = parse_args()
    if args.job_only and args.question_only:
        raise SystemExit("--job-only and --question-only cannot be used together.")

    load_env_file(Path(args.env_file))

    cohere_api_key = os.environ.get("COHERE_API_KEY")
    if not cohere_api_key:
        raise SystemExit("COHERE_API_KEY must be set in environment or env file.")

    model = os.environ.get("APP_CORPUS_EMBEDDING_MODEL", "embed-v4.0")
    output_dimension = int(os.environ.get("APP_CORPUS_EMBEDDING_OUTPUT_DIMENSION", "1024"))
    batch_size = args.batch_size or int(os.environ.get("APP_CORPUS_EMBEDDING_BATCH_SIZE", "32"))

    stats = SyncStats()
    session = create_requests_session(cohere_api_key)
    conn = connect()
    try:
        with conn:
            with conn.cursor() as cur:
                if not args.question_only:
                    stats.job_posting_embeddings_upserted = sync_dataset(
                        cur,
                        session,
                        JOB_POSTING_SELECT_SQL,
                        UPSERT_JOB_POSTING_SQL,
                        args.limit,
                        batch_size,
                        model,
                        output_dimension,
                    )
                if not args.job_only:
                    stats.question_embeddings_upserted = sync_dataset(
                        cur,
                        session,
                        QUESTION_SELECT_SQL,
                        UPSERT_QUESTION_SQL,
                        args.limit,
                        batch_size,
                        model,
                        output_dimension,
                    )
    finally:
        session.close()
        conn.close()

    print("Embedding sync completed")
    print(f"jobPostingEmbeddingsUpserted={stats.job_posting_embeddings_upserted}")
    print(f"questionEmbeddingsUpserted={stats.question_embeddings_upserted}")
    print(f"embeddingModel={model}")
    print(f"batchSize={batch_size}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
