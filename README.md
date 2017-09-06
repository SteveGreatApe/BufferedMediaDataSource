BufferedMediaDataSource
==========
  Implementation of MediaDataSource to layer over any InputStream

  This was initially created to use with SmbFile to play Media from a network drive, but will work
 for any InputStream, you just need a provide an implementation of BufferedMediaDataSource.StreamCreator
 that creates the stream and provides the length of the stream.

  Currently functionality is reasonably well tested with the provided unit tests, but more work
 needs to be done on performance analysis to find good numbers for buffer sizes and cache & read
 ahead buffer counts.

  The buffer sizes and counts can be specified at run time using the constructor override that
 takes an extra BufferedMediaDataSource.BufferConfig parameter. It would make sense for client
 code to calculate these values at runtime if it has an idea of the requirements for the media it
 is playing.

Unit tests
==========

MediaPlayerTest
---------------
  This has two parts, the first plays back the test files in Assets via the BufferedMediaDataSource
 interface. The second looks in the folder "BmdsTest" and runs the tests on any videos it finds
 in there.

BufferedMediaDataSourceTest
---------------------------
  This runs various tests over a dummy test stream using a variety of stream and buffer lengths. It
 also has multi-threaded tests to flush out any synchronisation issues.

Demo App
========
  The demo app is provided to test playing videos from the network. Currently you need to hand edit
 the path in the source code to specify the network drive you wish to play videos from, then enter
 username/password in the app to read a list of videos to play back.

  Supplied sample videos are taken with thanks from http://www.sample-videos.com/

JCIFS
=====
  The version of JCIFS used in the demo app is built from the 1.3.18 source with the 
 LargeReadWrite patch applied.