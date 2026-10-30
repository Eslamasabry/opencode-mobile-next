# KitHeroQuestion — the one big question a first page opens on (2026-10-20)

Unit: vision slice "Welcome is one question" (docs/qa/vision-welcome-2026-10-20).

## Purpose

The heading of a page that is one question ("Where should your agent run?"). Display type, larger than a screen title, so the question is the first and only thing read before the answers under it (`KitChoiceCards`). One per page, only on a page that is a question; status pages use `KitStatusAnswer`, ordinary pages their top bar or `KitPageHeading`.

## API

```dart
KitHeroQuestion(String question, {Key? key, Key? textKey})
```

## Look

48/54, weight 700, tracking −3.6 % (zero under Arabic), primary text colour, at the start of the page's rails. Under 340 dp of width it steps down to 32 so no word breaks inside itself. It grows with the person's text size only up to 1.15 times (display type is already large; the answers stay near the first screen at 200 %).

## States

`States: none` — it shows the words it is given.

## Accessibility

A header node with the question as its label.

## Tests

`test/kit/kit_hero_question_test.dart`, gallery `test/goldens/kit/kit_hero_question_golden_test.dart`, G6 scene in `test/kit/kit_welcome_overflow_scenes.dart`.
