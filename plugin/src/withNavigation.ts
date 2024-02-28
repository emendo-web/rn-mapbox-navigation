// import { ExpoConfig } from 'expo/config';

import {
    AndroidConfig,
    ConfigPlugin,
    createRunOncePlugin,
    withAndroidManifest,
} from '@expo/config-plugins';

// Source: https://docs.expo.dev/config-plugins/development-and-debugging/#manually-run-a-plugin
// Used for https://bitbucket.org/emendo-fr/react-native-mapbox-navigation/src/master/README.md
// For android we need to create custom plugin for add MAPBOX_ACCESS_TOKEN to AndroidManifest.xml
// For iOS we can set it in app.config.ts because infoPlist will be merged during build process

// Using helpers keeps error messages unified and helps cut down on XML format changes.
const { addMetaDataItemToMainApplication, getMainApplicationOrThrow } =
    AndroidConfig.Manifest;

type WithNavigationProps = {
    /**
     * @description Mapbox access token renquired for show map
     */
    mapboxAccessToken: string;
};

/**
 * Create plugin history for better debugging
 * cmd expo config --type prebuild
 */
//docs.expo.dev/config-plugins/development-and-debugging/#add-plugins-to-pluginhistory

let pkg: { name: string; version?: string } = {
    name: '@kienso/rn-mapbox-navigation',
    version: 'UNVERSIONED',
};

const withNavigation: ConfigPlugin<WithNavigationProps> = (
    config,
    pluginProps
) => {
    return withAndroidManifest(config, async (config) => {
        // Modifiers can be async, but try to keep them fast.
        config.modResults = await setCustomConfigAsync(
            config.modResults,
            pluginProps
        );
        return config;
    });
};

// Splitting this function out of the mod makes it easier to test.
async function setCustomConfigAsync(
    androidManifest: AndroidConfig.Manifest.AndroidManifest,
    pluginProps: WithNavigationProps
): Promise<AndroidConfig.Manifest.AndroidManifest> {
    const mapboxAccessToken = pluginProps.mapboxAccessToken;
    // Get the <application /> tag and assert if it doesn't exist.
    const mainApplication = getMainApplicationOrThrow(androidManifest);

    addMetaDataItemToMainApplication(
        mainApplication,
        // value for `android:name`
        'MAPBOX_ACCESS_TOKEN',
        // value for `android:value`
        mapboxAccessToken
    );

    return androidManifest;
}

export default createRunOncePlugin(withNavigation, pkg.name, pkg.version);
