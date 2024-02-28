import type { ViewStyle } from 'react-native';

/** @type {[number, number]}
 * Provide an array with longitude and latitude [$longitude, $latitude]
 */
declare type Coordinate = [number, number];
declare type OnLocationChangeEvent = {
    nativeEvent?: {
        latitude: number;
        longitude: number;
    };
};
declare type OnRouteProgressChangeEvent = {
    nativeEvent?: {
        distanceTraveled: number;
        durationRemaining: number;
        fractionTraveled: number;

        /**
         * @description
         * The remaining distance in meters until the user reaches the end of the route (in meters).
         */
        distanceRemaining: number;
    };
};
declare type OnErrorEvent = {
    nativeEvent?: {
        message?: string;
    };
};

declare type Edge = {
    top: number;
    bottom: number;
};

/**
 * @description MapEdge. Padding for the map
 */
declare type MapEdge = {
    top: number;
    bottom: number;
    left: number;
    right: number;
};

export interface IMapboxNavigationProps {
    origin: Coordinate;
    destination: Coordinate;
    shouldSimulateRoute?: boolean;
    onLocationChange?: (event: OnLocationChangeEvent) => void;
    onRouteProgressChange?: (event: OnRouteProgressChangeEvent) => void;
    onError?: (event: OnErrorEvent) => void;

    /**
     * @description
     * Called when the user clicks the back button on the navigation view
     * @returns {void}
     */
    onCancelNavigation?: () => void;
    /**
     * @description
     * Called when the user arrives at the destination
     * @returns {void}
     */
    onArrive?: () => void;
    showsEndOfRouteFeedback?: boolean;
    hideStatusView?: boolean;
    /**
     * @description
     * Mute or unmute voice instructions
     */
    mute?: boolean;
    style?: ViewStyle;
    /**
     * @description
     * padding for trip status view and card maneuver view
     */
    edge?: Edge;
    mapEdge?: MapEdge;
}
export {};
