# docker

담당: 이지원

Docker Compose로 전체 스택을 한 번에 띄웁니다. (공통 요구사항 9번)

포함할 컨테이너: MariaDB(논리 DB 3개 `user_db`·`policy_db`·`engagement_db`, 서비스별 계정 분리), Redis, Kafka, Zipkin(선택), discovery·config·gateway, 서비스 4개

```bash
cp ../.env.example ../.env   # 값 채우기
docker compose up -d --build
```

`docker-compose.yml`은 서비스 골격이 만들어진 뒤 추가합니다.
