# Metadata
Name:	ChiaraSelect2Speak
Author: Luca Randazzo
Date:   July 2020

## License
GNU GPLv3
This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, version 3 of the License.
This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
See the GNU General Public License for more details here: https://www.gnu.org/licenses/.

# Information
## Short description
ChiaraSelect2Speak is an Android accessibility service that enables to read out loud the text inside a user-selected area of the screen.
When active, the service overlays on top of any visible content on the screen, allowing to transform text it into a spoken message through OCR and TTS functionalities.

The service is inspired by the super cool Android "Select to Speak" accessibility service (https://support.google.com/accessibility/android/answer/7349565?hl=en).
However, differently than the latter, this service as well enables to read text which is not directly exposed to the Android operating system, e.g. the text inside apps and videogames.

Demo video: https://youtu.be/mUp831sS0lo

The service has been successfully tested under:
- Galaxy Tab A (2016) [SM-T585]
- Android: 8.10 (Oreo) [API level: 27]

Repository: https://github.com/HackaHealth-Geneva/ChiaraSelect2Speak

## Inspiration
I developed this service to enable my sister, Chiara, to independently play with her favourite videogames. Find below a short summary post and video :)
https://www.linkedin.com/feed/update/urn:li:activity:6668395321850134528/ 
[a higher quality video is available here: https://youtu.be/4ejiA0Va988]
An enormous thanks to Google for enabling developers to easily create assistive and accessible software thanks to the great Android APIs.

# Functioning
The service works as follows:
- The user activates the service from the Accessibility Settings of the Android device
- Once active, a "Start" button is showed on top of other apps
- When the user presses the button, the service takes a screenshot of the whole screen
- The screenshot is overlayed on top of the screen
- The user drags his/her finger on the screen (i.e. on the screenshot) to draw a rectangle around the area of interest
- The service crops the screenshot to the area of interest, and it applies OCR and TTS in order to recognize the text inside the cropped image and speak it out loud

# Resources
Many freely-available resources were used for the development of this service.
An enormous thanks to Google for providing lots of advanced functions and easy-to-use APIs through their Android OS, and many thanks to the developers whose code helped to create several parts of this service.
Following, a non-comprehensive list of some of these inspiring resources is provided.

## Android Accessibility services
### Tutorials
- https://codelabs.developers.google.com/codelabs/developing-android-a11y-service/#0
- https://developer.android.com/guide/topics/ui/accessibility/service
- https://developer.android.com/guide/topics/ui/accessibility

### Other services
- https://github.com/google/talkback
- https://github.com/googlecodelabs/android-accessibility

### References
- https://developer.android.com/reference/android/accessibilityservice/AccessibilityService

## Screenshot
- https://stackoverflow.com/questions/51320978/android-taking-a-screenshot-with-mediaprojectionmanager-onimageavailable-doesn
- https://stackoverflow.com/questions/32513379/how-to-record-screen-and-take-screenshots-using-android-api
- https://github.com/mtsahakis/MediaProjectionDemo

### Handling bitmaps
- https://stackoverflow.com/questions/15789049/crop-a-bitmap-image
- https://stackoverflow.com/questions/16804404/create-a-bitmap-drawable-from-file-path/16804467

## OCR
- https://github.com/googlesamples/android-vision
- https://medium.com/@prakash_pun/text-recognition-for-android-using-google-mobile-vision-a8ffabe3f5d6
- https://github.com/prakashpun/TextRecognitionAndroid
- https://firebase.google.com/docs/ml-kit/android/recognize-text
- https://developers.google.com/vision/android/text-overview
- https://www.programcreek.com/java-api-examples/?api=com.google.android.gms.vision.text.TextRecognizer

## TTS
- https://code.tutsplus.com/tutorials/android-sdk-using-the-text-to-speech-engine--mobile-8540

## UI
### Processing touch events
- https://stackoverflow.com/questions/30074771/how-to-detect-whether-screen-is-touched-in-a-background-service-in-android-studi
- https://developer.android.com/training/gestures

### Draw overlay
- https://stackoverflow.com/questions/4481226/creating-a-system-overlay-window-always-on-top
- http://android-er.blogspot.com/2013/09/detect-touch-and-draw-rect-on-bitmap.html

# ToDo
Below, some ideas for improvement of this service are provided.

## OCR
- Automated speaking of text within an area of interest, also if not all of the text has been selected by the user, or the user simply clicked on the text instead of creating a selection rectangle.
This could help users who are not able to finely create a selection rectangle (e.g. with impaired fine motor control) to use the service.
The "Android Select to Speak" service implements something similar.

## UI
- The UI button ("Start") should be movable by the user around the screen, not to cover potential areas of interest. The "Android Select to Speak" service implements this feature. 
- The UI should enable to Stop the TTS-engine (e.g. if a very large section of text has been selected, which is not of interest any longer). Potentially, Pause/Rewind features could be implemented as well.
The "Android Select to Speak" service implements similar features. 

## Language and Locale
- Select the locale of the TTS-engine.
- Provide error messages and debug information to the user through the TTS-engine in the selected locale.

## Goodies
- Select welcome message by TTS-engine.