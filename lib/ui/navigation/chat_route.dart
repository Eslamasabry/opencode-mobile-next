class ChatRouteArguments {
  const ChatRouteArguments({this.discardIfUntouched = false});

  const ChatRouteArguments.newlyCreated() : discardIfUntouched = true;

  final bool discardIfUntouched;
}
