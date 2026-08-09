package com.metalurgica.estoque.service;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.metalurgica.estoque.domain.entity.OrdemServico;
import com.metalurgica.estoque.domain.repository.OrdemServicoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Geração de PDF da OS fora da thread da requisição HTTP (não bloqueia o
 * fechamento da OS). Antes era feito via evento Kafka; @Async cumpre o mesmo
 * papel sem depender de um broker externo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfService {

    private final OrdemServicoRepository ordemServicoRepository;

    @Async
    @Transactional(readOnly = true)
    public void gerarPdfAsync(Long osId) {
        OrdemServico os = ordemServicoRepository.findById(osId).orElse(null);
        if (os == null) {
            log.error("OS {} nao encontrada para geracao de PDF", osId);
            return;
        }

        String codigoOs = os.getCodigo();
        log.info("Iniciando geracao de PDF para OS: {}", codigoOs);

        try {
            File dir = new File("pdfs_gerados");
            if (!dir.exists()) {
                dir.mkdir();
            }

            File pdfFile = new File(dir, codigoOs + ".pdf");
            Document document = new Document();
            PdfWriter.getInstance(document, new FileOutputStream(pdfFile));

            document.open();
            document.add(new Paragraph("===== ORDEM DE SERVICO ====="));
            document.add(new Paragraph("Codigo: " + os.getCodigo()));
            document.add(new Paragraph("Cliente: " + os.getEmpresa().getNome()));
            document.add(new Paragraph("Descricao: " + os.getDescricao()));
            document.add(new Paragraph("Status: " + os.getStatus()));
            document.add(new Paragraph("Mao de Obra: R$ " + os.getValorMaoDeObra()));
            document.add(new Paragraph("Observacoes: " + (os.getObservacao() != null ? os.getObservacao() : "N/A")));
            document.add(new Paragraph("Data Abertura: " + os.getDataAbertura()));
            document.add(new Paragraph("Data Conclusao: " + os.getDataConclusao()));
            document.close();

            log.info("PDF gerado com sucesso em: {}", pdfFile.getAbsolutePath());
        } catch (Exception e) {
            log.error("Erro ao gerar PDF para OS {}", codigoOs, e);
        }
    }
}
