# scache

scache is a JDWP packet accelerator. It works by guessing which JWDP
packet cmd will be sent by the debugger, sending them ahead of time, caching
the reply, and serving them instantly to the debugger when it later requests
them.

It works as a look-aside cache which the underlying transport layer must actively
collaborate with. Each packet sent or received should be presented to scache.
Upon presentation, scache returns a response which may "edict" to:

 - Discard the packet (cache hit).
 - Inject a reply (cache hit).
 - Inject a cmd (speculate).

## Packet direction names
To keep track of directions, scache has its own convention.

A packet doing to the debugged process is UPSTREAM.
A packet going to the debugger process is DOWNSTREAM.

```
           ┌──────────────┐
           │   Debugged   │
           └─────▲───┬────┘
                 │   │
           ┌─────┴───▼────┐
           │   Debugger   │
           └──────────────┘
```

# Speculating on packets

scache gets it name from how it speculates on JWPD traffic. Its full
name is "Speculative Cache".

## Speculator
The brain of scache, it is the piece which decides which cmd and reply
to listen to and what to do when it sees them.

## Trigger
Speculating on packet means inspecting either cmd or replies. Cmds are easy
to inspect because they contain a cmd ID and a cmdset ID. Replies however are
hard because they don't contain cmd ID and cmdset ID. They only contain a
JDWP ID and the cmd ID/cmdset ID should be inferred from that.

This is the problem a Trigger solves. The Speculator can request a trigger
on a cmdset/cmd on a cmd or a reply without having to keep track of pairs.

## Synthetic packets

When it speculates, scache creates a cmd which is to be injected with the
rest of the JDWP traffic. These are called synthetic cmds. Likewise, replies
to synthetic cmds are called synthetic replies.

## IDs

Since scache injects packets, it is important to not use packet ID overlapping
with the debugger IDs.

```
JDI debugger starts at 0x00000000
DDMLib starts at       0x40000000
adb_connection starts  0x80000000

scache starts at       0xA0000000
```

## Retagging

Because scache speculates with synthetic commands, the synthetic replies
do not have the IDs used by the debugger. Upon a cache hit, before sending
the reply back to the debugger, the packet needs to be re-tagged with the
ID contained in the cmd packet. This is done so the debugger can match our
synthetic reply to its organic cmd.

## Keyable

After it speculate, scache needs a way to cache packets and retrieve them
fast in case of cache HIT. The concept of Keyable solves this problem.

A cmd contains a 8-bit cmdset and a 8-bit cmd. This is the Type of a
cmd. When we add the parameters, we get a uniquely identifiable cmd. It is
called its Key.

Example: The SourceFile Command uses cmdset=ReferenceType(7) and cmd=SourceFile(2).
However, 7-2 is not enough to identify and cache a reply to this cmd. What is also
need is the parameter to the command: referenceTypeID.

The type of this command is therefore "7-2" but the key is "7-2-X" where X is the
referenceID. This is what is used to store a reply.

## Response edict vs journal

SCache works like a collaborative look-aside cache. Upon receiving a cmd or an event, it returns
two list of packets, one to send upstream, the other to send downstream. These are the EDICTs.

What if you have a jwpd tracing library? Can you not simply trace the outputs of scache? No, it
does not work upon cache hit. e.g.:

1. An upstream cmd arrives to scache
2. scache already speculated on this cmd and received the reply from the VM. This is a cache hit.
   It returns an empty upstream list and the speculatively cached response in the downstream list.
3. If we were to send these two edicts lists to a tracer, we would have a reply without a cmd
   (since scache prevented it from being sent).

This is what the Journal list is for. It is meant to be consumed by a JDWP tracer library. It gives a
cohesive view of the JDWP traffic, not what actually transited on the wire.

# Logging

jdwp-scache defines its own custom `SCacheLogger` to ensure compatibility
with a wide range of logging facilities.

To set logging in Android Studio, open the "Debug Log Settings" dialog. e.g:

```
scache:all
```

Alternatively, you can select `all` in the idea.log window.
