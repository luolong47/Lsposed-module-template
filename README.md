# Chastify Device-Owner Bridge (LSPosed-модуль)

LSPosed-модуль, который заставляет систему считать приложение **Chastify**
(`net.chastify.app`) владельцем устройства (device owner) — **без реального
provisioning и без второго приложения (Owndroid)**.

## Как это работает

Модуль грузится в `system_server` (scope = `system`) и хукает внутренние
проверки владельца в `DevicePolicyManagerService`:

- `isDeviceOwner(CallerIdentity …)`
- `isProfileOwner(CallerIdentity …)`
- перегрузки этих методов, принимающие `ComponentName`

Хук срабатывает **только когда вызывающий — Chastify**: в этом случае проверка
возвращает `true`, и система реально применяет запрос Chastify
(`addUserRestriction` и т.п.). Для всех остальных приложений и компонентов
системы вызывается оригинальный метод, так что побочных эффектов нет.

Методы `DevicePolicyManagerService` — внутренние, не из SDK, и их сигнатуры
меняются между версиями Android, поэтому они ищутся **рефлексивно во время
выполнения** (по имени + типу аргумента), а не указываются при компиляции. За
счёт этого модуль не привязан к одной версии Android.

## Важно перед использованием

1. **Chastify всё равно нужно включить как обычного device-admin**
   (Настройки → приложения администратора устройства). Модуль подделывает
   только повышение «админ → владелец», но не саму регистрацию админа — иначе
   система не найдёт `ActiveAdmin` и упадёт ещё до проверки владельца.
2. **Android 14+**: часть DPC-вызовов переведена на модель permission-based
   access (разрешения `MANAGE_DEVICE_POLICY_*`) вместо проверки владельца. Для
   user-restrictions старый путь пока работает; если какой-то конкретный вызов
   всё же упадёт с `SecurityException`, нужно будет добавить ещё один хук на
   проверку разрешения — смотри сообщение в `logcat`.

## Сборка

GitHub Actions (`.github/workflows/build.yml`) собирает debug-APK на каждый push.
Готовый `app-debug.apk` лежит в артефактах сборки. Локально:

```
./gradlew assembleDebug
```

## Установка

1. Установить APK.
2. В LSPosed включить модуль, в области действия (scope) оставить **System
   Framework** (`system`).
3. Перезагрузиться.
4. Включить Chastify как администратора устройства.
5. Проверить `logcat | grep ChastifyBridge` — должно быть
   `installed N owner-check hook(s)`.

## Структура

- `app/src/main/java/com/example/module/BridgeHook.java` — точка входа и хуки.
- `META-INF/xposed/java_init.list` — класс входа для libxposed.
- `META-INF/xposed/scope.list` — область действия (`system`).
- `META-INF/xposed/module.prop` — метаданные модуля.

## Зависимости

Собрано на новом API [libxposed](https://github.com/libxposed/api) `102.0.0`
(`compileOnly`, в APK не попадает).
