import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.system.MemoryStack.stackPush;

public class EafConverter {
    public static void main(String[] args) {
        if (args.length < 2 || args[0].equals("help")) {
            System.out.println("Verwendung: java -jar ./eaf-converter.jar <Ordner/Datei zum Konvertieren> <Skalierung>");
            return;
        }
        File file = new File(args[0]);
        if (!file.exists()) {
            System.out.println(args[0] + " ist nicht eine Datei oder ein Ordner.");
            return;
        }
        int scale = Integer.parseInt(args[1]);

        if (file.isDirectory()) convertMany(file, scale);
        else if (file.getName().endsWith("_0.png")) convertAnim(file, scale);
        else if (file.getName().endsWith(".png") && !file.getName().contains("_")) convertSingle(file, scale);
    }

    public static void convertMany(File dir, int scale) {
        if (!dir.isDirectory()) return;
        for (File file : dir.listFiles()) {
            String name = file.getName();
            if (file.isDirectory()) convertMany(file, scale);
            else if (name.endsWith("_0.png")) convertAnim(file, scale);
            else if (name.endsWith(".png") && !name.contains("_")) convertSingle(file, scale);
        }
    }

    public static void convertAnim(File frame0, int scale) {
        System.out.println("Animation umwandeln: " + frame0.getPath());

        try (MemoryStack stack = stackPush()) {
            // Der Dateiname ohne Erweiterung und Framenummer
            String baseName = frame0.getName().substring(0, frame0.getName().length() - 4).split("_")[0];

            // Für den eaf-Header
            int frameCount = 0;
            int width = -1, height = -1;

            Map<Integer, String> frames = new HashMap<>();

            // Einzelframes finden und sammeln
            for (File file : frame0.getParentFile().listFiles()) {
                String name = file.getName();
                if (!name.endsWith(".png") || !name.startsWith(frame0.getName().substring(0, frame0.getName().indexOf('_') + 1)))
                    continue;

                IntBuffer pWidth = stack.callocInt(1), pHeight = stack.callocInt(1), channels = stack.callocInt(1);
                ByteBuffer pixels = STBImage.stbi_load(file.getPath(), pWidth, pHeight, channels, 4);
                frames.put(Integer.parseInt(name.substring(file.getName().indexOf('_') + 1, file.getName().length() - 4)), convertToEaf(pixels, pWidth.get(0), pHeight.get(0)));
                STBImage.stbi_image_free(pixels.rewind());

                if (width == -1 && height == -1) {
                    width = pWidth.get(0);
                    height = pHeight.get(0);
                } else if (width != pWidth.get(0) && height != pHeight.get(0)) {
                    System.out.println("Bild " + file.getAbsolutePath() + " hat eine andere Größe als die vorherigen Bilder: [" + pWidth.get(0) + "," + pHeight.get(0) + "] statt [" + width + "," + height + "].");
                    return;
                }

                frameCount++;
            }

            StringBuilder eaf = new StringBuilder();

            // Frames in richtiger Reihenfolge zum StringBuilder hinzufügen
            for (int i = 0; i < frames.size(); i++) {
                String frame = frames.get(i);
                if (frame == null) {
                    System.out.println("Frame " + i + " der Animation " + baseName + " wurde nicht gefunden.");
                    return;
                }
                eaf.append(frame).append("-\n");
            }

            // Unnötiges "-\n" vom Ende entfernen
            eaf.delete(eaf.length() - 3, eaf.length() - 1);

            // Header am Anfang einfuegen
            eaf.insert(0, "_fig_\nan:" + frameCount + "\nf:" + scale + "\nx:" + width + "\ny:" + height + "\np:0\nq:0\n-\n");

            Files.writeString(Path.of(frame0.getParentFile().getPath(), baseName + ".eaf"), eaf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void convertSingle(File file, int scale) {
        System.out.println("Einzelbild umwandeln: " + file.getPath());

        try (MemoryStack stack = stackPush()) {
            StringBuilder eaf = new StringBuilder();

            IntBuffer pWidth = stack.callocInt(1), pHeight = stack.callocInt(1), channels = stack.callocInt(1);
            ByteBuffer pixels = STBImage.stbi_load(file.getPath(), pWidth, pHeight, channels, 4);
            eaf.append(convertToEaf(pixels, pWidth.get(0), pHeight.get(0)));
            STBImage.stbi_image_free(pixels.rewind());

            // Unnötiges "\n" (aus convertToEaf) vom Ende entfernen
            eaf.deleteCharAt(eaf.length() - 1);

            // Header am Anfang einfuegen
            eaf.insert(0, "_fig_\nan:1\nf:" + scale + "\nx:" + pWidth.get(0) + "\ny:" + pHeight.get(0) + "\np:0\nq:0\n-\n");

            Files.writeString(Path.of(file.getPath().substring(0, file.getPath().length() - 4) + ".eaf"), eaf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String convertToEaf(ByteBuffer pixels, int width, int height) {
        StringBuilder eaf = new StringBuilder();

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Pixel an dieser Position lesen
                byte[] values = new byte[4];
                pixels.position((x + y * width) * 4);
                pixels.get(values, 0, 4);

                // Positionsangabe
                eaf.append('Z').append(x).append('-').append(y).append(':');

                // Pixelwert
                if (values[3] == 0) eaf.append("%%;\n");
                else
                    eaf.append('&').append(values[0]).append(',').append(values[1]).append(',').append(values[2]).append(";\n");
            }
        }

        return eaf.toString();
    }
}
