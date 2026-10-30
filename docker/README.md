# docker

Docker Compose로 전체 스택을 한 번에 띄웁니다. (공통 요구사항 9번)

포함할 컨테이너: MariaDB(서비스별 DB 4개), Redis, Kafka, Zipkin(선택), discovery·config·gateway, 서비스 5개

```bash
cp ../.env.example ../.env   # 값 채우기
docker compose up -d --build
```

`docker-compose.yml`은 서비스 골격이 만들어진 뒤 추가합니다.
