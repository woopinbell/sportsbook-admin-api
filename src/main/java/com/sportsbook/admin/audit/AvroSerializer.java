package com.sportsbook.admin.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

/**
 * Encodes an Avro {@link SpecificRecord} to its binary form for the Kafka value (ADR-0006 / 0014 —
 * Avro without a schema registry in V1). Same approach as the other services' producers.
 */
public final class AvroSerializer {

  private AvroSerializer() {}

  public static byte[] toBytes(SpecificRecord record) {
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      DatumWriter<SpecificRecord> writer = new SpecificDatumWriter<>(record.getSchema());
      BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
      writer.write(record, encoder);
      encoder.flush();
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Avro serialization failed", e);
    }
  }
}
