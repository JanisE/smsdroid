# SMSdroid

SMSdroid is a light and fast app for reading and sending text messages.
It is optimized to play well with any app capable of sending messages.
A good example is [WebSMS](https://github.com/felixb/websms).

## Fork modifications

I've added a feature to **select and bulk-delete** many threads or conversations (as opposed to 
deleting only one specific conversation or all SMSes on the device, which was already implemented).

There is a hard-coded pause of 40 s after each conversation deletion, in order to avoid deletion exceptions in the case of there being too many messages and not enough phone resources to deal with in and delete conversations properly.

### Issues and TODOs

I have no intention on tackling the issues or TODOs I've added to the code in the near future 
and probably ever.

## Translation

Help translating this app to your favorite langauge on [crowdin](https://crowdin.com/project/smsdroid/invite)
