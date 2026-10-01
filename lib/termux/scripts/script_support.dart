/// Paths and helpers every embedded Termux script shares.
const termuxHomeDirectory = '/data/data/com.termux/files/home';
const termuxManagerPath = '$termuxHomeDirectory/.oc/manager.sh';
const termuxManagedServerPort = 4096;

String shellQuote(String value) => "'${value.replaceAll("'", "'\"'\"'")}'";
