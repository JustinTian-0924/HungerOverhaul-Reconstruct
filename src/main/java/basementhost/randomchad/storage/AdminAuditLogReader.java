package basementhost.randomchad.storage;

import basementhost.randomchad.data.AdminAuditEntry;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Reads completed audit lines (newest first) without loading or writing the full log.
public final class AdminAuditLogReader {
	private static final int PAGE_SIZE = 5;
	private static final String QUOTED = "\"(?:[^\"\\\\]|\\\\[\"\\\\])*+\"";
	private static final Pattern ENTRY = Pattern.compile(
			"(?<time>\\S+) actor=(?<actor>" + QUOTED + ") actor_type=\\w+ actor_uuid=\\S+"
					+ " target=(?<target>" + QUOTED + ") target_uuid=(?<uuid>[0-9a-fA-F-]{36})"
					+ " target_mode=(?<mode>\\w+) operation=(?<operation>\\w+) field=(?<field>\\w+) amount=(?<amount>\\S+)"
					+ " outcome=(?<outcome>\\w+) before=(?<before>\\{[^}]*\\}) after=(?<after>\\{[^}]*\\})"
	);
	private static final Pattern VALUES = Pattern.compile(
			"\\{nutrition=([^,]+),fullness=([^,]+),nutrition_frozen=(true|false),fullness_frozen=(true|false)\\}"
	);

	private final Path logFile;

	public AdminAuditLogReader(Path logFile) {
		this.logFile = logFile;
	}

	public Page readPage(String targetName, UUID targetId, int pageNumber) throws IOException {
		if (pageNumber < 1) {
			throw new IllegalArgumentException("Page numbers start at 1");
		}
		long skip = (pageNumber - 1L) * PAGE_SIZE;
		boolean foundMatch = false;
		List<Entry> entries = new ArrayList<>(PAGE_SIZE);

		try (SeekableByteChannel channel = Files.newByteChannel(logFile, StandardOpenOption.READ)) {
			ReverseLines lines = new ReverseLines(channel);
			String line;
			while ((line = lines.next()) != null) {
				Entry entry = parseEntry(line, targetName, targetId);
				if (entry == null) {
					continue;
				}
				foundMatch = true;
				if (skip > 0L) {
					skip--;
					continue;
				}
				if (entries.size() == PAGE_SIZE) {
					return new Page(entries, true, true);
				}
				entries.add(entry);
			}
		} catch (NoSuchFileException exception) {
			return new Page(List.of(), false, false);
		}
		return new Page(entries, foundMatch, false);
	}

	private static Entry parseEntry(String line, String targetName, UUID targetId) {
		Matcher match = ENTRY.matcher(line.stripTrailing());
		if (!match.matches()) {
			return null;
		}

		String name = unquote(match.group("target"));
		if (targetId == null && !name.equalsIgnoreCase(targetName)) {
			return null;
		}

		try {
			UUID loggedPlayerId = UUID.fromString(match.group("uuid"));
			if (targetId != null && !targetId.equals(loggedPlayerId)) {
				return null;
			}
			return new Entry(
					Instant.parse(match.group("time")),
					unquote(match.group("actor")),
					name,
					loggedPlayerId,
					AdminAuditEntry.TargetMode.valueOf(match.group("mode")),
					match.group("operation"),
					match.group("field"),
					match.group("amount"),
					AdminAuditEntry.Outcome.valueOf(match.group("outcome")),
					parseValues(match.group("before")),
					parseValues(match.group("after"))
			);
		} catch (IllegalArgumentException | DateTimeParseException exception) {
			// Ignore damaged lines and keep looking for valid audit records.
			return null;
		}
	}

	private static AdminAuditEntry.Values parseValues(String text) {
		Matcher match = VALUES.matcher(text);
		if (!match.matches()) {
			throw new IllegalArgumentException("Invalid audit values");
		}
		double nutrition = Double.parseDouble(match.group(1));
		double fullness = Double.parseDouble(match.group(2));
		if (!Double.isFinite(nutrition) || !Double.isFinite(fullness)) {
			throw new IllegalArgumentException("Non-finite audit values");
		}
		return new AdminAuditEntry.Values(nutrition, fullness, Boolean.parseBoolean(match.group(3)),
				Boolean.parseBoolean(match.group(4)));
	}

	private static String unquote(String text) {
		StringBuilder result = new StringBuilder(text.length() - 2);
		for (int index = 1; index < text.length() - 1; index++) {
			char character = text.charAt(index);
			result.append(character == '\\' ? text.charAt(++index) : character);
		}
		return result.toString();
	}

	public record Page(List<Entry> entries, boolean hasMatches, boolean hasNextPage) {
		public Page {
			entries = List.copyOf(entries);
		}
	}

	public record Entry(
			Instant timestamp,
			String actorName,
			String targetName,
			UUID targetId,
			AdminAuditEntry.TargetMode targetMode,
			String operation,
			String field,
			String amount,
			AdminAuditEntry.Outcome outcome,
			AdminAuditEntry.Values before,
			AdminAuditEntry.Values after
	) {
	}

	private static final class ReverseLines {
		private final SeekableByteChannel channel;
		private final ByteBuffer block = ByteBuffer.allocate(8192);
		private final byte[] line = new byte[16384];
		private long offset;
		private int bytesLeft;
		private boolean foundLineEnd;

		private ReverseLines(SeekableByteChannel channel) throws IOException {
			this.channel = channel;
			this.offset = channel.size();
		}

		private String next() throws IOException {
			int length = 0;
			boolean oversized = false;
			int value;
			while ((value = readByte()) >= 0) {
				if (value == '\n') {
					boolean complete = foundLineEnd && length > 0 && !oversized;
					foundLineEnd = true;
					if (complete) {
						return decodeLine(length);
					}
					length = 0;
					oversized = false;
				} else if (foundLineEnd) {
					if (length < line.length) {
						line[line.length - ++length] = (byte) value;
					} else {
						oversized = true;
					}
				}
			}
			return length > 0 && !oversized ? decodeLine(length) : null;
		}

		private String decodeLine(int length) {
			try {
				return StandardCharsets.UTF_8.newDecoder()
						.decode(ByteBuffer.wrap(line, line.length - length, length)).toString();
			} catch (CharacterCodingException exception) {
				return "";
			}
		}

		private int readByte() throws IOException {
			if (bytesLeft == 0) {
				if (offset == 0L) {
					return -1;
				}
				int count = (int) Math.min(offset, block.capacity());
				offset -= count;
				channel.position(offset);
				block.clear().limit(count);
				while (block.hasRemaining()) {
					if (channel.read(block) < 0) {
						throw new EOFException("Audit log was truncated during the query");
					}
				}
				bytesLeft = count;
			}
			return Byte.toUnsignedInt(block.get(--bytesLeft));
		}
	}
}
