package org.gbif.hadoop.compress.d2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.zip.InflaterInputStream;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Closer;

/**
 * Utilities that help use D2 correctly, and in particular the setting up of streams.
 * <p/>
 * For examples of use see the unit tests (D2CompressionTest in particular).
 */
@SuppressWarnings("CyclicClassDependency")
public final class D2Utils {

  public static final String FILE_EXTENSION = ".def2";

  /**
   * Utility to construct an input stream suitable for reading a d2 file in isolation.
   *
   * @param in An input stream which should provide raw d2 bytes
   *
   * @return An input stream prepared to handle footers correctly
   */
  public static FooteredInputStream prepareD2Stream(InputStream in) {
    return new FooteredInputStream(in, D2Footer.FOOTER_LENGTH_ISOLATED_READ);
  }

  /**
   * Utility to provide a decompressing input stream that wraps the source, which should be a footer-less stream of
   * raw compressed bytes.
   */
  public static InputStream decompressInputSteam(InputStream in) {
    return new InflaterInputStream(in, new D2Decompressor());
  }

  /**
   * Utility to prepare streams for reading in concatenation using {@link java.io.SequenceInputStream}.
   *
   * @param sources Streams which provide raw d2 bytes order by the manner in which they should be concatenated
   *
   * @return Input streams prepared to handle footers correctly
   */
  public static List<FooteredInputStream> prepareD2Streams(Iterable<InputStream> sources) {
    Iterator<InputStream> iter = sources.iterator();
    List<FooteredInputStream> result = Lists.newArrayList();
    while (iter.hasNext()) {
      //noinspection resource
      InputStream in = iter.next();
      if (iter.hasNext()) {
        result.add(new FooteredInputStream(in, D2Footer.FOOTER_LENGTH));
      } else {
        // the last stream needs the close marker in the footer (important!)
        result.add(new FooteredInputStream(in, D2Footer.FOOTER_LENGTH_ISOLATED_READ));
      }
    }
    return result;
  }

  /**
   * Merges the content of the incoming streams of compressed content onto the output stream which are all then closed.
   *
   * @param compressed streams of compressed content
   * @param target     to write to
   */
  public static void decompress(Iterable<InputStream> compressed, OutputStream target) throws IOException {
    Closer closer = Closer.create();
    closer.register(target);
    try (
      InputStream in = decompressInputSteam(D2CombineInputStream.build(compressed))
    ) {
      ByteStreams.copy(in, target);
      target.flush(); // probably unnecessary but not guaranteed by close()
    } finally {
      closer.close();
    }
  }

  /**
   * Decompresses the content of the incoming stream of compressed content onto the output stream which are both then
   * closed.
   *
   * @param compressed stream of compressed content
   * @param target     to write to
   */
  public static void decompress(InputStream compressed, OutputStream target) throws IOException {
    Closer closer = Closer.create();
    closer.register(target);
    try (
      InputStream in = decompressInputSteam(compressed)
    ) {
      ByteStreams.copy(in, target);
      target.flush();  // probably unnecessary but not guaranteed by close()
    } finally {
      closer.close();
    }
  }

  /**
   * Compresses the incoming stream of uncompressed content onto the target stream, which are both then closed.
   *
   * @param uncompressed incoming stream of uncompressed bytes
   * @param target       target of the compression
   */
  public static void compress(InputStream uncompressed, OutputStream target) throws IOException {
    Closer closer = Closer.create();
    closer.register(target);
    try (
      D2OutputStream compressed = new D2OutputStream(target)
    ) {
      ByteStreams.copy(uncompressed, compressed);
      target.flush(); // probably unnecessary but not guaranteed by close()
    } finally {
      closer.close();
    }
  }

  private D2Utils() {}
}