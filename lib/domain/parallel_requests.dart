/// Attach handlers to all requests immediately, retaining their individual
/// result types and the original error type for gateway error mapping.
Future<(A, B, C)> waitForRequests<A, B, C>(
  Future<A> first,
  Future<B> second,
  Future<C> third,
) async {
  late A a;
  late B b;
  late C c;
  await Future.wait<void>([
    first.then((value) {
      a = value;
    }),
    second.then((value) {
      b = value;
    }),
    third.then((value) {
      c = value;
    }),
  ]);
  return (a, b, c);
}
