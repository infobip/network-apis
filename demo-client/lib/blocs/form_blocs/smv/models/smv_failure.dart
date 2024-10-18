class SMVFailure {
  final String errorDescription;
  final String msisdn;
  final String? token;
  final String? deviceIp;
  final int? devicePort;
  final String? additionalErrorDescription;

  SMVFailure(
    this.errorDescription,
    this.msisdn, {
    this.token,
    this.deviceIp,
    this.devicePort,
    this.additionalErrorDescription,
  });
}
