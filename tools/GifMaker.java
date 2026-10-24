import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/** Собирает зацикленный GIF из PNG-кадров: java GifMaker.java <out.gif> <delayMs> <width> <frame...> */
public class GifMaker {
	public static void main(String[] args) throws Exception {
		File out = new File(args[0]);
		int delayCs = Integer.parseInt(args[1]) / 10;
		int width = Integer.parseInt(args[2]);
		String[] frames = Arrays.copyOfRange(args, 3, args.length);
		ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
		try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
			writer.setOutput(ios);
			writer.prepareWriteSequence(null);
			for (String f : frames) {
				BufferedImage src = ImageIO.read(new File(f));
				int h = src.getHeight() * width / src.getWidth();
				BufferedImage img = new BufferedImage(width, h, BufferedImage.TYPE_INT_RGB);
				Graphics2D g = img.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
				g.drawImage(src, 0, 0, width, h, null);
				g.dispose();
				ImageWriteParam param = writer.getDefaultWriteParam();
				IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(img), param);
				String fmt = meta.getNativeMetadataFormatName();
				IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(fmt);
				IIOMetadataNode gce = child(root, "GraphicControlExtension");
				gce.setAttribute("disposalMethod", "none");
				gce.setAttribute("userInputFlag", "FALSE");
				gce.setAttribute("transparentColorFlag", "FALSE");
				gce.setAttribute("delayTime", Integer.toString(delayCs));
				gce.setAttribute("transparentColorIndex", "0");
				IIOMetadataNode apps = child(root, "ApplicationExtensions");
				IIOMetadataNode app = new IIOMetadataNode("ApplicationExtension");
				app.setAttribute("applicationID", "NETSCAPE");
				app.setAttribute("authenticationCode", "2.0");
				app.setUserObject(new byte[]{1, 0, 0});
				apps.appendChild(app);
				meta.setFromTree(fmt, root);
				writer.writeToSequence(new IIOImage(img, null, meta), param);
			}
			writer.endWriteSequence();
		}
		System.out.println(out + " " + out.length() / 1024 + " KB, " + frames.length + " frames");
	}

	private static IIOMetadataNode child(IIOMetadataNode root, String name) {
		for (int i = 0; i < root.getLength(); i++) {
			if (root.item(i).getNodeName().equalsIgnoreCase(name)) return (IIOMetadataNode) root.item(i);
		}
		IIOMetadataNode node = new IIOMetadataNode(name);
		root.appendChild(node);
		return node;
	}
}
