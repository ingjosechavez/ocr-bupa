package mx.com.bupa.ocr.controller;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@RestController
@RequestMapping("/api/v1")
public class SimpleOCRController {

	@PostMapping(value = "/pdf/extractText", produces="application/json")
	public @ResponseBody ResponseEntity<String> extractTextFromPDFFile(@RequestParam("file") MultipartFile file) {
		try {
			// Load file into PDFBox class
			PDDocument document = PDDocument.load(file.getBytes());
			PDFTextStripper stripper = new PDFTextStripper();
			String strippedText = stripper.getText(document);
			JSONObject obj = new JSONObject();
			// Check text exists into the file
			if (strippedText.trim().isEmpty()) {
				obj.put("message", "Attachment not a PDF");
				return new ResponseEntity<String>(obj.toString(), HttpStatus.BAD_REQUEST);
			}

			obj.put("fileName", file.getOriginalFilename());

			String[] strings = strippedText.split("\\r\\n");
			obj.put("text", strippedText.toString());
			String companyName = getTextLine(strings, "Denominación/Razón Social:");
			if(companyName.isBlank()) {
				extractPersonInfo(strings, obj);
			} else {
				extractCompanyInfo(strings, obj);
			}
			return new ResponseEntity<String>(obj.toString(), HttpStatus.OK);
		} catch (Exception e) {
			JSONObject obj = new JSONObject();
			obj.put("message", e.getMessage());
			return new ResponseEntity<String>(obj.toString(), HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@GetMapping("/pdf/ping")
	public ResponseEntity<String> get() {
		return new ResponseEntity<String>("PONG", HttpStatus.OK);
	}

	private void extractCompanyInfo(String[] content, JSONObject obj) {
		String rfc = getTextLine(content, "RFC:");
		String name = getTextLine(content, "Denominación/Razón Social:");
		String capital = getTextLine(content, "Régimen Capital:");
		String commercialName = getTextLine(content, "Nombre Comercial:");
		String fiscalInfo = getFiscalInfo(content);
		String zipCode = getTextLine(content, "Código Postal:").split(" ")[0].trim();
		String email = getTextLine(content, "Y Calle:").split("Correo Electrónico:")[1].trim();
		
		obj.put("rfc", rfc);
		obj.put("razonSocial", name);
		obj.put("regimenCapital", capital);
		obj.put("nombreComercial", commercialName);
		obj.put("regimenFiscal", fiscalInfo);
		obj.put("codigoPostal", zipCode);
		obj.put("email", email);
	}

	private void extractPersonInfo(String[] content, JSONObject obj) {
		String rfc = getTextLine(content, "RFC:");
		String name = getTextLine(content, "Nombre (s):");
		String lastName = getTextLine(content, "Primer Apellido:");
		String middleName = getTextLine(content, "Segundo Apellido:");
		String fiscalInfo = getFiscalInfo(content);
		String zipCode = getTextLine(content, "Código Postal:").split(" ")[0].trim();
		String email = getTextLine(content, "Y Calle:").split("Correo Electrónico:")[1].trim();
		
		obj.put("rfc", rfc);
		obj.put("nombre", name);
		obj.put("apellidoPaterno", lastName);
		obj.put("apellidoMaterno", middleName);
		obj.put("regimenFiscal", fiscalInfo);
		obj.put("codigoPostal", zipCode);
		obj.put("email", email);
	}
	
	private String getTextLine(String[] content, String lineInit) {
		String result = "";
		for (String string : content) {
			if (string.startsWith(lineInit)) {
				result = string.replace(lineInit, "").trim();
				break;
			}
		}
		return result;
	}

	private String getFiscalInfo(String[] content) {
		String result = "";
		for(int i = 0; i < content.length; i++) {
			String line = content[i];
			if(line.startsWith("Régimen")) {
				result = content[i+1].replaceAll("([\\d]{2}[/]?)", "").trim();
				break;
			}
		}
		return result;
	}
	
	private String extractTextFromScannedDocument(PDDocument document) throws IOException, TesseractException {

// Extract images from file
		PDFRenderer pdfRenderer = new PDFRenderer(document);
		StringBuilder out = new StringBuilder();

		ITesseract _tesseract = new Tesseract();
		_tesseract.setDatapath("/usr/share/tessdata/");
		_tesseract.setLanguage("ita"); // choose your language

		for (int page = 0; page < document.getNumberOfPages(); page++) {
			BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);

// Create a temp image file
			File temp = File.createTempFile("tempfile_" + page, ".png");
			ImageIO.write(bim, "png", temp);

			String result = _tesseract.doOCR(temp);
			out.append(result);

// Delete temp file
			temp.delete();

		}

		return out.toString();

	}

}
