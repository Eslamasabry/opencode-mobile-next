import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

/// Product verbs and destinations, independent of the underlying icon package.
///
/// Regular is the action weight. Selected navigation keeps its silhouette and
/// adds a quiet duotone fill; More stays as dots. Static references let Flutter
/// remove unused font glyphs when building a release. Artwork is Phosphor 2.1.0
/// (MIT); native IconData keeps compatibility with Flutter 3.47.2.
@staticIconProvider
abstract final class AppIconography {
  static const navigationSize = 24.0;
  static const actionSize = 24.0;
  static const inlineSize = 20.0;

  static const workspace = IconData(
    0xe17e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const workspaceSelected = IconData(
    0xe17f,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  );
  static const files = IconData(
    0xe25a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const filesSelected = IconData(
    0xe25b,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  );
  static const activity = IconData(
    0xe0d0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const activitySelected = IconData(
    0xe0d1,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  );
  static const more = IconData(
    0xe1fe,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const menu = IconData(
    0xe208,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const add = IconData(
    0xe3d4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const send = IconData(
    0xe08e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const stop = IconData(
    0xe46c,
    fontFamily: 'AppPhosphorFill',
    matchTextDirection: false,
  );
  static const settings = IconData(
    0xe434,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const search = IconData(
    0xe30c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const branch = IconData(
    0xe278,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const terminal = IconData(
    0xeae8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const copy = IconData(
    0xe1cc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const expand = IconData(
    0xe0a6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const close = IconData(
    0xe4f6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const back = IconData(
    0xe058,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const retry = IconData(
    0xe036,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const externalLink = IconData(
    0xe5de,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const review = IconData(
    0xe27c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const chevronRight = IconData(
    0xe13a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const chevronDown = IconData(
    0xe136,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const chevronUp = IconData(
    0xe13c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const check = IconData(
    0xe182,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const info = IconData(
    0xe2ce,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const warning = IconData(
    0xe4e0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const error = IconData(
    0xe4f8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cloud = IconData(
    0xe1aa,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cloudOff = IconData(
    0xe1b6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const server = IconData(
    0xe2a0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const model = IconData(
    0xe74e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const agent = IconData(
    0xe762,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const tools = IconData(
    0xe5d4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const extensions = IconData(
    0xe596,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const keyboard = IconData(
    0xe2d8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const guide = IconData(
    0xe0e6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const bug = IconData(
    0xe5f4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const appearance = IconData(
    0xe6c8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const privacy = IconData(
    0xe40c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const diagnostics = IconData(
    0xe000,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const usage = IconData(
    0xe150,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const link = IconData(
    0xe2e2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const unlink = IconData(
    0xe2e4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const attach = IconData(
    0xe39a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const download = IconData(
    0xe20c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const upload = IconData(
    0xe4c0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const file = IconData(
    0xe230,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const code = IconData(
    0xe1bc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const archive = IconData(
    0xe00c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const delete = IconData(
    0xe4a6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const edit = IconData(
    0xe3b4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const pin = IconData(
    0xe3e2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const unpin = IconData(
    0xe3e4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const computer = IconData(
    0xe560,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const folderAdd = IconData(
    0xe25e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const question = IconData(
    0xe3e8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const permissions = IconData(
    0xe2d6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const clock = IconData(
    0xe19a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const star = IconData(
    0xe46a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const starFilled = IconData(
    0xe46a,
    fontFamily: 'AppPhosphorFill',
    matchTextDirection: false,
  );
  static const mic = IconData(
    0xe326,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const image = IconData(
    0xe2ca,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const camera = IconData(
    0xe10e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  // Additional product vocabulary for coherent page adoption.
  static const accessibility = IconData(
    0xecfe,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const account = IconData(
    0xe4c4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const addCircle = IconData(
    0xe3d6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const alignLeft = IconData(
    0xe484,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const article = IconData(
    0xe0a8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const batteryCharging = IconData(
    0xe0ba,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const batteryWarning = IconData(
    0xe0c8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const blocked = IconData(
    0xe3de,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const bookmark = IconData(
    0xe0ea,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const bookmarks = IconData(
    0xe5f0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const browser = IconData(
    0xe0f4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const calendar = IconData(
    0xe10a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cameraOff = IconData(
    0xe110,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const category = IconData(
    0xec5e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const chat = IconData(
    0xe168,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const checkCircle = IconData(
    0xe184,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const checkboxChecked = IconData(
    0xe186,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const checkboxEmpty = IconData(
    0xe45e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const checklist = IconData(
    0xeadc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const checks = IconData(
    0xe53a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const chevronLeft = IconData(
    0xe138,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const clearAll = IconData(
    0xec54,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cloudCheck = IconData(
    0xe1b0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const collapse = IconData(
    0xe09e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const contrast = IconData(
    0xe18c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const cut = IconData(
    0xeae0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const darkMode = IconData(
    0xe330,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const dataObject = IconData(
    0xe860,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const database = IconData(
    0xe1de,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const deviceOff = IconData(
    0xee46,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const down = IconData(
    0xe03e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const editNote = IconData(
    0xe34c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const editOff = IconData(
    0xecf6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const experiments = IconData(
    0xe79e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const feedback = IconData(
    0xe17a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const fileText = IconData(
    0xe23a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const fileUpload = IconData(
    0xe61e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const filter = IconData(
    0xe268,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const filterOff = IconData(
    0xe26c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const folderOpen = IconData(
    0xe256,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const folders = IconData(
    0xe260,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const fork = IconData(
    0xe27e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const forward = IconData(
    0xe06c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const function = IconData(
    0xebe4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const globe = IconData(
    0xe288,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const hidden = IconData(
    0xe224,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const history = IconData(
    0xe1a0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const idea = IconData(
    0xe2dc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const imageBroken = IconData(
    0xe7a8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const images = IconData(
    0xe836,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const inbox = IconData(
    0xe010,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const launch = IconData(
    0xe3fe,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const layers = IconData(
    0xe466,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const lightMode = IconData(
    0xe472,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const lightning = IconData(
    0xe2de,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const locked = IconData(
    0xe308,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const login = IconData(
    0xe428,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const lowPriority = IconData(
    0xe03e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const manageAccount = IconData(
    0xe4cc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const nested = IconData(
    0xe046,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const network = IconData(
    0xedde,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const networkCheck = IconData(
    0xee74,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const networkOff = IconData(
    0xeddc,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const note = IconData(
    0xe348,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const notificationImportant = IconData(
    0xe5ea,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const outbox = IconData(
    0xee52,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const package = IconData(
    0xe390,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const paste = IconData(
    0xe198,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const person = IconData(
    0xe4c2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const personRemove = IconData(
    0xe4ce,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const phone = IconData(
    0xe1e0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const play = IconData(
    0xe3d0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const playCircle = IconData(
    0xe3d2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const policy = IconData(
    0xe40c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const privacyWarning = IconData(
    0xe412,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const processor = IconData(
    0xe610,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const projects = IconData(
    0xe102,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const qrCode = IconData(
    0xe3e6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const queueAdd = IconData(
    0xe2f8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const radioEmpty = IconData(
    0xe18a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const radioSelected = IconData(
    0xeb08,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const removeCircle = IconData(
    0xe32c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const reply = IconData(
    0xe024,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const restart = IconData(
    0xe038,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const restore = IconData(
    0xe1a0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const returnKey = IconData(
    0xe782,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const save = IconData(
    0xe248,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const searchList = IconData(
    0xebe0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const secureNetwork = IconData(
    0xe40c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const settingsAdvanced = IconData(
    0xe272,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const shield = IconData(
    0xe40a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const sparkle = IconData(
    0xe6a2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const speakUser = IconData(
    0xeca8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const speed = IconData(
    0xee74,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const statusDot = IconData(
    0xe18a,
    fontFamily: 'AppPhosphorFill',
    matchTextDirection: false,
  );
  static const stopCircle = IconData(
    0xe46e,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const support = IconData(
    0xe584,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const supportQuestion = IconData(
    0xe3e8,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const swap = IconData(
    0xe0a0,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const sync = IconData(
    0xe094,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const systemDownload = IconData(
    0xe20c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const systemTheme = IconData(
    0xe18c,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const text = IconData(
    0xe484,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const textShort = IconData(
    0xe484,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const textSnippet = IconData(
    0xe484,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const timeline = IconData(
    0xe5a2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const timer = IconData(
    0xe492,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const touch = IconData(
    0xec90,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const unarchive = IconData(
    0xee52,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const undo = IconData(
    0xe08a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: true,
  );
  static const unfoldLess = IconData(
    0xe532,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const unfoldMore = IconData(
    0xe140,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const usageRing = IconData(
    0xeaa6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const visible = IconData(
    0xe220,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const volume = IconData(
    0xe44a,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const waiting = IconData(
    0xe2b6,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const waitingEmpty = IconData(
    0xe2b2,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const waitingStart = IconData(
    0xe2b4,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const waveform = IconData(
    0xe802,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
  static const zip = IconData(
    0xe958,
    fontFamily: 'AppPhosphorRegular',
    matchTextDirection: false,
  );
}

// Static background glyphs preserve release font tree shaking.
const _duotoneBackgrounds = <int, IconData>{
  0xe17f: IconData(
    0xe17e,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  ),
  0xe25b: IconData(
    0xe25a,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  ),
  0xe0d1: IconData(
    0xe0d0,
    fontFamily: 'AppPhosphorDuotone',
    matchTextDirection: false,
  ),
};

/// Renders regular and duotone glyphs with one optional accessibility label.
///
/// This is decoration, not a tap target: place it inside an IconButton or other
/// accessible control. Let that control's tooltip or visible text name the
/// action; use [semanticLabel] only for a standalone informative glyph.
class AppGlyph extends StatelessWidget {
  const AppGlyph(
    this.icon, {
    super.key,
    this.size,
    this.color,
    this.semanticLabel,
    this.textDirection,
  });

  final IconData icon;
  final double? size;
  final Color? color;
  final String? semanticLabel;

  /// Pass an explicit direction for technical marks that must not mirror.
  /// Navigation arrows follow the surrounding direction by default; technical
  /// glyph data preserves its orientation.
  final TextDirection? textDirection;

  @override
  Widget build(BuildContext context) {
    final foreground = Icon(
      icon,
      size: size,
      color: color,
      textDirection: textDirection,
    );
    final secondary = icon.fontFamily == 'AppPhosphorDuotone'
        ? _duotoneBackgrounds[icon.codePoint]
        : null;
    final glyph = ExcludeSemantics(
      child: secondary == null || MediaQuery.highContrastOf(context)
          ? foreground
          : Stack(
              alignment: Alignment.center,
              children: [
                Opacity(
                  opacity: .2,
                  child: Icon(
                    secondary,
                    size: size,
                    color: color,
                    textDirection: textDirection,
                  ),
                ),
                foreground,
              ],
            ),
    );
    final label = semanticLabel;
    return label == null
        ? glyph
        : Semantics(label: label, image: true, child: glyph);
  }
}

/// The open portal identity without a launcher background or shadow.
///
/// Decorative by default. A standalone mark may supply [semanticLabel]; a
/// neighboring app title already communicates the identity and needs no label.
class AppBrandMark extends StatelessWidget {
  const AppBrandMark({super.key, this.size = 32, this.semanticLabel});

  final double size;
  final String? semanticLabel;

  @override
  Widget build(BuildContext context) {
    final mark = SvgPicture.asset(
      'assets/branding/open-portal/mark.svg',
      width: size,
      height: size,
      colorFilter: ColorFilter.mode(
        Theme.of(context).colorScheme.primary,
        BlendMode.srcIn,
      ),
      excludeFromSemantics: true,
    );
    final label = semanticLabel;
    return label == null
        ? mark
        : Semantics(label: label, image: true, child: mark);
  }
}
