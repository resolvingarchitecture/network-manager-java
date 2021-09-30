package ra.networkmanager;

public enum ResponseCodes {
    READY,

    LOCAL_PEER_FOR_NETWORK_NOT_AVAILABLE,
    NEXT_ROUTE_MUST_BE_AN_EXTERNAL_ROUTE,
    NEXT_ROUTE_NOT_EXTERNAL,
    SERVICE_NOT_FOUND_FOR_NETWORK,
    UNABLE_TO_DETERMINE_EXTERNAL_ROUTE,
    UNABLE_TO_SELECT_PEER_NETWORK
}
