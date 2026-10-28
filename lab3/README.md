# GeoBrief: погода и интересные места рядом

Приложение на Kotlin с графическим интерфейсом (Swing, своя тёмная тема). Вводите название места, выбираете вариант из списка и получаете:

- **Локации** (GraphHopper): название, адрес, тип объекта OSM (`osm_key` / `osm_value`, например `highway` / `bus_stop`, `place` / `square`), координаты.
- **Погоду** (MET Norway): температура, ветер, влажность, **давление в мм рт. ст.**, состояние неба.
- **Интересные места** (Википедия): название, расстояние от точки, краткое описание и картинка статьи.

Все HTTP-вызовы асинхронные (`HttpClient.sendAsync` + `CompletableFuture`), независимые запросы идут параллельно, зависимые собраны в цепочку без блокировок, результат собирается в один объект `Report` и выводится целиком.

## API

| Роль | API | Константа в `Config` |
|---|---|---|
| Поиск локаций | [GraphHopper Geocoding](https://docs.graphhopper.com/#operation/getGeocode) (нужен ключ) | `GEOCODING_URL` |
| Погода | [MET Norway Locationforecast 2.0](https://api.met.no/weatherapi/locationforecast/2.0/documentation) | `WEATHER_URL` |
| Места рядом | [Wikipedia GeoData](https://www.mediawiki.org/wiki/Extension:GeoData#API) (`list=geosearch`) | `PLACES_URL` |
| Описание и картинка | [Wikipedia TextExtracts + PageImages](https://www.mediawiki.org/wiki/Extension:TextExtracts#API) по id статьи | `DESCRIPTION_URL` |

## Запуск

Нужны JDK 17+ и Gradle (или IntelliJ IDEA: откройте папку проекта, зелёная стрелка у `fun main()` в `Main.kt`).

```
gradle run
```

Ключ GraphHopper по умолчанию прописан в `Config.kt` (`DEFAULT_GRAPHHOPPER_KEY`). Его можно сменить:
- в окне программы, поле «Ключ GraphHopper» (запоминается между запусками);
- переменной окружения `GRAPHHOPPER_KEY`.

**Не публикуйте ключ в открытом репозитории.** Перед `git push` уберите его из `Config.kt` и пользуйтесь полем в окне или переменной окружения.

В `Config.kt` также: `USER_AGENT` (замените контакт на свой, MET Norway и Wikimedia этого требуют), `MAX_PLACES`, `DEFAULT_RADIUS_M`.

## Как пользоваться

1. Введите название (например `Цветной проезд, Новосибирск` или `Красная площадь`) и нажмите Enter или «Найти».
2. Выберите вариант в списке слева. Под названием показаны адрес и тип объекта.
3. Справа появятся погода и места. Радиус поиска мест (500 м ... 10 км) переключается кнопками, отчёт перезагружается сразу.

## Консоль

Выводится только отладка опроса API: адрес (ключ скрыт), код ответа и время.

```
12:01:02.331 [API] OK    GET graphhopper.com/api/1/geocode?q=...&key=*** -> HTTP 200 (412 ms)
12:01:03.020 [API] ERROR GET api.met.no/weatherapi/locationforecast/2.0/compact?... -> HTTP 403 (95 ms)
```

Ошибки (`ERROR`) показываются и в интерфейсе: в строке состояния и в карточке отчёта.

## Структура

```
src/main/kotlin/
├── Main.kt                 точка входа: GuiApp.create().run()
└── APICalling/
    ├── Config.kt           адреса API, User-Agent, ключ, лимиты
    ├── model/              Location, Weather, Place, Report, Outcome
    ├── net/                JsonHttpClient (async GET + лог), ApiLog, Urls
    ├── api/                GeocodingApi, WheatherApi.kt (WeatherApi), PlaceApi.kt (PlacesApi), DescriptionApi
    ├── service/            LocationService (п.2), ReportService (п.4 и 5)
    └── ui/                 GuiApp, MainPanel, LocationList, ReportView, Theme, Components, Fmt, Settings
```

Зависимости идут сверху вниз: `ui` → `service` → `api` → `net`.

## Возможные ошибки

| Сообщение | Причина |
|---|---|
| `HTTP 401/403` от graphhopper.com | неверный ключ или исчерпан лимит запросов |
| `HTTP 403` от api.met.no / wikipedia | не заменён `USER_AGENT` |
| `HTTP 429` | слишком частые запросы, подождите |
| «ничего не нашлось» в местах | в радиусе нет статей Википедии, увеличьте радиус |
