# S3 setup for job posting images

## 1. Apply bucket CORS

```bash
aws s3api put-bucket-cors \
  --bucket "$S3_BUCKET" \
  --cors-configuration file://ops/s3/job-posting-image-cors.json
```

Verify the applied CORS configuration:

```bash
aws s3api get-bucket-cors \
  --bucket "$S3_BUCKET"
```

## 2. Apply lifecycle policy

```bash
aws s3api put-bucket-lifecycle-configuration \
  --bucket "$S3_BUCKET" \
  --lifecycle-configuration file://ops/s3/job-posting-image-lifecycle.json
```
