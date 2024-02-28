# @kienso/rn-mapbox-navigation

Smart Mapbox turn-by-turn routing based on real-time traffic for React Native. A navigation UI ready to drop into your React Native application.

## Setup environment (Android)

### Configure secret token

> https://docs.mapbox.com/android/navigation/build-with-coreframework/installation#configure-your-secret-token

### Configure public token

> https://docs.mapbox.com/android/navigation/build-with-coreframework/installation#resources

### Install dependencies

From the root project we will be run yarn. This will install all the dependencies for the project and the example app. (Check .yarnrc)

```shell
$ yarn
```

## Start example app

```shell
adb reverse tcp:8081 tcp:8081
```

From the root directory

```shell
yarn example start
```

## Run example app on Android

Open android folder from example directory in Android Studio and run the app on your real or virtual device.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)
