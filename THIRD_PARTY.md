# Сторонние компоненты

* Paper API 26.3.build.223-stable — только зависимость компиляции; в JAR не включён.
  https://github.com/PaperMC/Paper
* Adventure — предоставляет Paper; отдельная копия не включена.
  https://github.com/KyoriPowered/adventure
* SQLite JDBC 3.53.4.0 — https://github.com/xerial/sqlite-jdbc , Apache-2.0/BSD согласно upstream.
  Включает native SQLite; SQLite является public domain.
* Gson 2.13.2 — https://github.com/google/gson , Apache-2.0.
* SLF4J API — транзитивная зависимость SQLite JDBC, MIT; https://www.slf4j.org/license.html .
* Gradle Wrapper 9.1.0 — Apache-2.0, https://github.com/gradle/gradle .
* JUnit 5.13.4 — только тестирование, EPL-2.0; https://junit.org/ .

В архив исходников не включены Minecraft/Paper server JAR, JDK, кэш Gradle и игровые миры.
Новые реплики, имена и чертежи созданы для LivingVillages; материалы Millénaire не копировались.
Free 0.1 использует стандартные текстуры Minecraft и не содержит ресурспака. Лицензия MIT этого проекта не распространяется на отдельную редакцию Premium.
