FROM python:3.13-alpine
WORKDIR /app
COPY proxy.py .
USER 65534
EXPOSE 18081
CMD ["python", "proxy.py"]
