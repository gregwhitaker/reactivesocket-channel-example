# reactivesocket-channel-example
[![Build Status](https://travis-ci.org/gregwhitaker/reactivesocket-channel-example.svg?branch=master)](https://travis-ci.org/gregwhitaker/reactivesocket-channel-example)

This example shows you how to open a bi-directional communications channel between applications using [Reactive Sockets](http://reactivesocket.io/).

The example starts two applications which both operate as a client and server.  Each application opens a single bi-directional channel 
that they use to push messages to the other application and receive responses to those messages.

## Running the Example
The example can be run using the following gradle command:

```
$ ./gradlew run
```

## License
Copyright 2016 Greg Whitaker

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.
