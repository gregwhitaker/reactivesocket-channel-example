reactivesocket-channel-example
===

This example shows you how to open a bi-directional communications channel between applications using [Reactive Sockets](http://reactivesocket.io/).

The example starts to applications which both operate as a client and server.  Each application opens a single bi-directional channel 
that they use to push messages to the other application and receive responses to those messages.

##Running the Example
The example can be run using the following gradle command:

```
$ ./gradlew run
```
