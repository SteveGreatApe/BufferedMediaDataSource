BufferedMediaDataSource
==========
  Implementation of MediaDataSource to layer over any InputStream

  This was initially created to use with SmbFile to play Media from a network drive, but will work
 for any InputStream, you just need a provide an implementation of BufferedMediaDataSource.StreamCreator
 that creates the stream and provides the length of the stream.
 
  In Version 0.2 support for DataInput has also been added to allow this to work random access
 sources such as RandomAccessFile SmbRandomAccessFile. This should theoretically be a bit more
 efficient than streamed sources, but actual results may vary depending on the underlying source.

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
  The demo app is provided to test playing videos from the network. This can browse the network and
  allow you to navigate to any available videos to test playback.

  Supplied sample videos are taken with thanks from http://www.sample-videos.com/

JCIFS
=====
   For Version 0.4 we've switched to using jcifs-ng from https://github.com/AgNO3/jcifs-ng/.

   jcifs-ng is being actively developed and already has significant performance gains over the
  original jcifs.

   At the moment we're on an unreleased development build of 2.1.0, once there's an official release
  we'll switch over to building against that.
