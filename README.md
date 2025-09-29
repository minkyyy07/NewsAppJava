# NewsApp (JavaFX)

Кратко
- Темы изменены: теперь «Политика» и «Спорт» (+ «Все»).
- Улучшена загрузка RSS: добавлен User-Agent, чистка HTML в описаниях.
- Безопасная загрузка CSS (без падения, если файла нет).

Требования
- JDK 17+
- Maven 3.8+

Сборка
```bash
mvn -DskipTests clean package
```

Запуск (рекомендуется)
```bash
mvn -DskipTests javafx:run
```
Так JavaFX подхватывается автоматически, и ошибка «JavaFX runtime components are missing…» не возникает.

Почему появляется «JavaFX runtime components are missing…»
- Это происходит при запуске JAR напрямую (`java -jar target/news-app-1.0.0.jar`) без указания модулей JavaFX на module-path. Решения:
  1) Использовать `mvn javafx:run` (просто).
  2) Собрать компактный runtime-образ и запускать его:
     ```bash
     mvn -DskipTests javafx:jlink
     ./target/image/bin/news-app
     ```
  3) Либо запускать вручную с module-path к артефактам JavaFX (не рекомендуется, зависит от платформы).

Замечания
- Ленты новостей:
  - Политика: Reuters, BBC, CNN
  - Спорт: Reuters, BBC Sport, CNN, ESPN
- Поиск работает по заголовку и описанию, есть пагинация и автодогрузка при прокрутке.

Проблемы и советы
- Если интерфейс не стартует и видите предупреждение про native access — плагин уже передаёт `--enable-native-access=javafx.graphics`.
- На слабой сети некоторые RSS могут падать по таймауту — приложение продолжит с оставшимися лентами.

