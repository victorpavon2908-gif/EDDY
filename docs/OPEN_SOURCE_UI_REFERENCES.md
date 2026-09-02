# Open-source UI references used by NIKO

NIKO's interface is original to this repository, but selected interaction and animation ideas were studied from open-source projects and then rewritten/adapted for Jetpack Compose.

## ai-assistant-android

- Project: `souravanand001/ai-assistant-android`
- Relevant source: `app/src/main/java/com/app/assistant/ui/screen/HandsFreeBar.kt`
- License: MIT
- Copyright: Copyright (c) 2025 Sourav Anand
- Use in NIKO: conceptual reference for state-driven organic blob motion, breathing, listening motion and speaking equalizer behavior. NIKO's implementation, layout, palette, particle system, neural core and monogram are custom.

MIT License notice:

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, subject to inclusion of the copyright and permission notice.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED.

## Canopas Compose Animations

- Project: `canopas/compose-animations-examples`
- License: Apache License 2.0
- Use in earlier NIKO iterations: reference for staggered expanding-wave animation patterns. The current 0.9.4 neural-core renderer uses a custom implementation.
