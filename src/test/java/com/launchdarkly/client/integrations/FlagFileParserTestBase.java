package com.launchdarkly.client.integrations;

import com.launchdarkly.client.integrations.FileDataSourceParsing.FileDataException;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FlagFileParser;
import com.launchdarkly.client.integrations.FileDataSourceParsing.FlagFileRep;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;

import static com.launchdarkly.client.integrations.FileDataSourceTestData.FLAG_VALUES;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.FULL_FLAGS;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.FULL_SEGMENTS;
import static com.launchdarkly.client.integrations.FileDataSourceTestData.resourceFilePath;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@SuppressWarnings("javadoc")
public abstract class FlagFileParserTestBase {
  private final FlagFileParser parser;
  private final String fileExtension;
  
  protected FlagFileParserTestBase(FlagFileParser parser, String fileExtension) {
    this.parser = parser;
    this.fileExtension = fileExtension;
  }
  
  @Test
  public void canParseFileWithAllProperties() throws Exception {
    try (FileInputStream input = openFile("all-properties")) {
      FlagFileRep data = parser.parse(input);
      assertThat(data.flags, equalTo(FULL_FLAGS));
      assertThat(data.flagValues, equalTo(FLAG_VALUES));
      assertThat(data.segments, equalTo(FULL_SEGMENTS));
    }
  }
  
  @Test
  public void canParseFileWithOnlyFullFlag() throws Exception {
    try (FileInputStream input = openFile("flag-only")) {
      FlagFileRep data = parser.parse(input);
      assertThat(data.flags, equalTo(FULL_FLAGS));
      assertThat(data.flagValues, nullValue());
      assertThat(data.segments, nullValue());
    }
  }
  
  @Test
  public void canParseFileWithOnlyFlagValue() throws Exception {
    try (FileInputStream input = openFile("value-only")) {
      FlagFileRep data = parser.parse(input);
      assertThat(data.flags, nullValue());
      assertThat(data.flagValues, equalTo(FLAG_VALUES));
      assertThat(data.segments, nullValue());
    }
  }
  
  @Test
  public void canParseFileWithOnlySegment() throws Exception {
    try (FileInputStream input = openFile("segment-only")) {
      FlagFileRep data = parser.parse(input);
      assertThat(data.flags, nullValue());
      assertThat(data.flagValues, nullValue());
      assertThat(data.segments, equalTo(FULL_SEGMENTS));
    }
  }
  
  @Test(expected = FileDataException.class)
  public void throwsExpectedErrorForBadFile() throws Exception {
    try (FileInputStream input = openFile("malformed")) {
      parser.parse(input);
    }
  }
  
  private FileInputStream openFile(String name) throws URISyntaxException, FileNotFoundException {
    return new FileInputStream(resourceFilePath(name + fileExtension).toFile());
  }
}
