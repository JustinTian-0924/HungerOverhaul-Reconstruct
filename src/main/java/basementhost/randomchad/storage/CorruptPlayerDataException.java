package basementhost.randomchad.storage;

public final class CorruptPlayerDataException extends PlayerDataStorageException {
	public CorruptPlayerDataException(String message) {
		super(message);
	}

	public CorruptPlayerDataException(String message, Throwable cause) {
		super(message, cause);
	}
}
