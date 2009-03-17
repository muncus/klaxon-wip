########################################
# Klaxon - Oncall Pager
########################################

Klaxon is an oncall pager app for android. It allows you to easily respond to
important messages with canned responses.

More documentation is forthcoming, but for now, its just a todo list.

TODO:
* remove ack/nack actions.
* per-receiver settings that dont have to be in preferences.xml
* email page receiver
* xmpp page receiver.
* add generic sanity checks (like the subject check from SmsPageReceiver) in PagerProvider.
* refactor out PageReceiver as an interface / base class.


PageReceiver "interface" - 
- receive an incoming message, decide if its a page, and insert it.
- receive intent for replying to a message. message text passed as an extra.
