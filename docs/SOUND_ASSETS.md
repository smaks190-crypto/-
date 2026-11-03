# David Finance sound assets

The short interface cues in `app/src/main/res/raw` are sourced from the **UISFX** sound pack by Romain Simon:
- Repository: https://github.com/romainsimon/uisfx
- Audio license: **CC0-1.0** (public domain dedication)
- Selected cues: Glass (open, close, send, receive, check, complete) and Arcade (achievement).

The splash-screen coin toss is intentionally **not** replaced by a generic UI cue. It continues to use the app's original procedural coin synthesis and its separately limited playback gain, preserving the established quiet coin chime.

Cue mapping:
- David Live open/close: Glass open/close
- Chat send/receive: Glass send/receive
- Voice operation recognized: Glass check
- Operation successfully saved: Glass complete
- Achievement unlocked: Arcade achievement
- The splash-screen coin toss remains its own original procedural effect.
