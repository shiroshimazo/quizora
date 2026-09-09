package quizora.model;

import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

public final class ProfilePicture {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private ProfilePicture() { }
    public static byte[] read(Path path) throws IOException {
        try (var in = Files.newInputStream(path)) {
            byte[] bytes = in.readNBytes(MAX_BYTES + 1);
            validate(bytes); return bytes;
        }
    }
    public static void validate(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Choose a PNG or JPEG image up to 2 MB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Choose a valid PNG or JPEG image.");
            var reader = readers.next();
            try {
                if (!java.util.List.of("png", "jpeg").contains(reader.getFormatName().toLowerCase(java.util.Locale.ROOT)))
                    throw new IllegalArgumentException("Only PNG and JPEG images are supported.");
                reader.setInput(input);
                if (reader.getWidth(0) > 4096 || reader.getHeight(0) > 4096)
                    throw new IllegalArgumentException("Image dimensions must not exceed 4096 by 4096 pixels.");
                if (reader.read(0) == null) throw new IllegalArgumentException("The selected image cannot be read.");
            } finally { reader.dispose(); }
        }
    }
}
