FROM python:3.12-slim
WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 LOKY_MAX_CPU_COUNT=2
COPY requirements-lock.txt .
RUN pip install --no-cache-dir -r requirements-lock.txt
COPY app ./app
COPY training ./training
RUN python -m training.train && useradd --system app && chown -R app:app /app
USER app
EXPOSE 8001
CMD ["python","-m","uvicorn","app.main:app","--host","0.0.0.0","--port","8001","--no-access-log"]
