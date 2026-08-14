abstract class AppConfiguration {
  // For IO: http://mobile-identity-smv-demo.ioinfobip.com
  static const String backendApiBaseUrl =
      'https://mobile-identity-smv-demo.infobip.com';
  static const List<Location> locations = [
    // Location.iov3,
    Location.sp3,
    Location.fr4,
    Location.munm,
  ];
  static const Location defaultLocation = Location.sp3;
  static const Duration requestTimeout = Duration(seconds: 10);
  static const int pollingRetryLimit = 3;
  static const Duration pollingRetryInterval = Duration(seconds: 2);
}

enum Location {
  // iov3,
  sp3,
  fr4,
  munm;

  static Location fromString(String location) {
    return Location.values.firstWhere((element) => element.name == location);
  }
}

extension LocationExtension on Location {
  String get title {
    switch (this) {
      // case Location.iov3:
      //   return 'IoV3';
      case Location.sp3:
        return 'Brazil';
      case Location.fr4:
        return 'Europe';
      case Location.munm:
        return 'India';
    }
  }
}
