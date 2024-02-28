import React, { useEffect } from 'react';

import MapboxNavigation from '@kienso/rn-mapbox-navigation';
import {
    Alert,
    Permission,
    PermissionsAndroid,
    Platform,
    StyleSheet,
    View,
} from 'react-native';

export default function App() {
    useEffect(() => {
        Platform.OS === 'android' && requestLocationPermission();
    }, []);

    const requestLocationPermission = async () => {
        try {
            await PermissionsAndroid.request(
                PermissionsAndroid.PERMISSIONS
                    .ACCESS_FINE_LOCATION as Permission
            );
        } catch (err) {
            console.warn(err);
        }
    };

    return (
        <View style={styles.container}>
            <MapboxNavigation
                origin={[2.4508048, 48.5691981]}
                destination={[2.300946, 48.606716]}
                style={styles.box}
                shouldSimulateRoute={true}
                showsEndOfRouteFeedback={true}
                onLocationChange={(event) => {
                    console.log('onLocationChange', event.nativeEvent);
                }}
                onRouteProgressChange={(event) => {
                    console.log('onRouteProgressChange', event.nativeEvent);
                }}
                onArrive={() => {
                    Alert.alert('You have reached your destination');
                }}
                onCancelNavigation={() => {
                    Alert.alert('Cancelled navigation event');
                }}
                onError={(event) => {
                    const message = event?.nativeEvent?.message;
                    if (message) Alert.alert(message);
                }}
                edge={{ top: 10, bottom: 20 }}
                mapEdge={{ top: 0, bottom: 0, left: 0, right: 0 }}
            />
        </View>
    );
}

const styles = StyleSheet.create({
    container: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
    },
    box: {
        width: '100%',
        height: '100%',
    },
});
