FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 7860

CMD ["gunicorn", "-w", "1", "-k", "gthread", "--threads", "4", "--timeout", "120", "server:app", "--bind", "0.0.0.0:7860"]