# Metrics service

## Описание
Сервис получает метрики от IoT устройства по http запросу и использует rolling average для сглаживания и z-score для аномалий.
Применяем redis для хранения rolling окон и состояния между несколькими инстансами.

## Swagger
Доступен по

http://localhost:8094/swagger-ui/index.html

## Сборка

Требуется Java 21 и Gradle.

### Сборка
```shell
./gradlew clean build
```

### Запуск
```shell
./gradlew bootRun
```

## Конфигурация

Настройки в `src/main/resources/application.yaml`:


| Переменная                       | Значение по умолчанию                  | Описание                                    |
|----------------------------------|----------------------------------------|---------------------------------------------|
| SERVER_PORT                      | 8094                                   | Порт сервиса                                |
| SPRING_APPLICATION_NAME          | metrics                                | Имя приложения                              |
| REDIS_HOST                       | 127.0.0.1                              | Адрес редис                                 |
| REDIS_PORT                       | 6379                                   | Порт редис                                  |
| REDIS_PASSWORD                   | authentik                              | Пароль редис                                |
