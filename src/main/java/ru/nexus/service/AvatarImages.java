package ru.nexus.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;

/** Decode bounded raster input and store a resized JPEG without original metadata. */
public final class AvatarImages {

  private AvatarImages() {}

  public static byte[] normalize(MultipartFile file) {
    if (file.isEmpty()) throw NexusService.fail(400, "Выберите фотографию");
    if (file.getSize() > 5 * 1024 * 1024) throw NexusService.fail(
      413,
      "Фото должно быть не больше 5 МБ"
    );
    try (
      var stream = file.getInputStream();
      var input = ImageIO.createImageInputStream(stream)
    ) {
      if (input == null) throw NexusService.fail(
        400,
        "Не удалось прочитать фото"
      );
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw NexusService.fail(
        400,
        "Выберите изображение JPEG или PNG"
      );
      var reader = readers.next();
      try {
        String format = reader.getFormatName();
        if (
          !format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")
        ) throw NexusService.fail(400, "Поддерживаются только JPEG и PNG");
        reader.setInput(input, true, true);
        int width = reader.getWidth(0),
          height = reader.getHeight(0);
        if (
          width < 1 || height < 1 || (long) width * height > 25_000_000
        ) throw NexusService.fail(
          400,
          "Разрешение фото должно быть не больше 25 мегапикселей"
        );
        var source = reader.read(0);
        double scale = Math.min(1, 1200.0 / Math.max(width, height));
        var image = new BufferedImage(
          Math.max(1, (int) (width * scale)),
          Math.max(1, (int) (height * scale)),
          BufferedImage.TYPE_INT_RGB
        );
        var graphics = image.createGraphics();
        try {
          graphics.setColor(Color.WHITE);
          graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
          graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BICUBIC
          );
          graphics.drawImage(
            source,
            0,
            0,
            image.getWidth(),
            image.getHeight(),
            null
          );
        } finally {
          graphics.dispose();
        }
        var output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", output)) throw new IOException(
          "JPEG encoder unavailable"
        );
        return output.toByteArray();
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException ex) {
      throw NexusService.fail(
        400,
        "Не удалось прочитать фото. Попробуйте другой JPEG или PNG"
      );
    }
  }
}
