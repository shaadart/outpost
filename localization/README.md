# Preserved engine translations

The first Outpost release ships in English. These inherited BitChat translations are preserved outside Android resources because they are incomplete and do not cover the Outpost wrapper. They are not packaged in the APK.

Before restoring a locale, translate the complete interface including profile, deletion, invitation and permission text, repair locale-specific plurals, and validate right-to-left layout, accessibility and formatting arguments. Keep strict lint enabled; do not copy English into locale resources to claim translation coverage.
