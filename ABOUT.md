# Why Outpost exists

Because a person nearby should still be someone you can reach when the usual network is gone.

Imagine two people waiting on the same platform. The signal has vanished. Both phones are charged. Inside each one, a small radio is ready to listen.

It seems a shame to leave those radios with nothing to say.

Outpost is an Android messenger built around that possibility: nearby people helping messages find their way, using the phones they already carry.

Perhaps the message is “I found your bag.” Perhaps it is “We’re by the east gate.” Or, with all the urgency a small human can muster, “Did you bring snacks?”

There should be room for all of these things in the air between us.

Outpost grows from BitChat’s networking foundation. Bluetooth lets participating phones nearby discover one another. The mesh can pass messages through other participating phones, giving them a path beyond a single connection. That path depends on the people, devices, and radio conditions around it.

Each phone can help a little. Together, those small contributions can make a conversation possible.

Outpost gives that machinery a familiar home. Choose a name. Find people nearby. Open a conversation. Create a group and share an invitation. Send a message, a small photo, a file, or a short voice note. The aim is to make these ordinary acts feel ordinary, even when the connection underneath them is unusual.

Your profile begins on your device. You can create it without a phone number or email address. Your phone creates the cryptographic keys used for your identity and private communication; a display name gives other people something human to call you. Identity verification helps when you need to check who is behind that name.

There is something lovely about mathematics doing a practical kindness. An encrypted message can travel through another phone while its words remain private to the people who hold the right keys. The person helping carry it can help without being invited into the conversation.

Private chats use BitChat’s Noise encryption. Outpost groups use a shared encryption key carried in the invitation. Anyone holding that invitation can read, send, and invite others, so sharing it deserves the same care as sharing a key to a room. The separate nearby public chat is visible to the mesh.

These differences should be easy to understand. A person deserves to know who can hear them.

They also deserve a way to put a conversation down.

Outpost keeps text history encrypted on the phone. You can clear a chat, leave a group, or ask Android to erase your profile and all the app’s data from that device. The next launch begins again with a new profile. Other people keep their copies, and files you have exported remain yours to manage. Erasure has a boundary, and the app should explain it plainly.

Privacy belongs among familiar things: closing a door, folding a letter, deciding which story to tell and when to stop telling it. We want the controls to carry that same quiet dignity.

The wish for a lighter app comes from a similar place. Attention, battery, and storage are things people lend to software. We should spend them carefully. Every extra feature asks for a little more of someone’s life; it ought to have a good reason for being there.

Outpost is still being built and tested. Nearby communication needs reachable peers. Batteries, distance, walls, and Android’s rules all matter. Transfers are small, and delivery can be delayed or fail. This is a release candidate: delivery between separate physical phones still needs further testing, and long-distance messaging through the inherited Tor and relay code remains unverified. The [README](README.md) and [validation report](docs/single-device-validation.md) describe what works today and what still needs care.

Keeping those limits visible is part of caring for the people who might use this.

The source is open under [GPLv3](LICENSE.md), so others can read it, question it, improve it, and carry the work forward. Outpost owes its foundation to BitChat and the people who built it. We hope to repay some of that generosity by making their work easier for more people to use.

An outpost is a small place from which someone can answer.

That is enough of a beginning: a radio, a little care, and another person within reach.
