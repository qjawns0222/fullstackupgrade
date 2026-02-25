[Fullstack] AI 분석 결과 PDF 리포트 자동 생성 시스템 구축

AI 분석 결과를 단순히 화면으로만 보는 것이 아니라, 문서화하여 보관하고 싶은 요구사항이 생겼다. 시니어 개발자로서 이러한 '리포트 다운로드' 기능은 서비스의 완성도를 결정짓는 핵심 요소라고 생각한다. 이번 미션에서는 OpenPDF 라이브러리를 활용해 AI 분석 결과(OCR 텍스트 등)를 PDF로 정교하게 렌더링하고 다운로드할 수 있는 기능을 구현했다.

### 기술적 도전: PDF 렌더링 및 스트림 처리

단순히 텍스트를 파일로 저장하는 것이 아니라, 문서의 구조(제목, 메타데이터, 결과 본문)를 잡고 폰트와 레이아웃을 설정하는 것이 핵심이었다. 또한, 서버 메모리 효율을 위해 `ByteArrayOutputStream`을 활용해 결과물을 생성하고, 이를 클라이언트로 신속하게 스트리밍하는 구조를 채택했다.

### 핵심 구현 코드 스니펫 (무조건 포함)

#### 1. PdfService: 도큐먼트 생성 로직
이 서비스는 `AnalysisRequest` 엔티티를 받아 OpenPDF를 통해 문서를 빌드한다.

```kotlin
@Service
class PdfService {
    fun generateAnalysisReport(request: AnalysisRequest): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document()
        PdfWriter.getInstance(document, out)
        
        document.open()
        
        val titleFont = Font(Font.HELVETICA, 18f, Font.BOLD)
        val normalFont = Font(Font.HELVETICA, 12f, Font.NORMAL)

        document.add(Paragraph("AI Analysis Report", titleFont))
        document.add(Paragraph("Filename: ${request.originalFileName}", normalFont))
        document.add(Paragraph("Analysis Result:", Font(Font.HELVETICA, 14f, Font.BOLD)))
        document.add(Paragraph(request.result ?: "No result available.", normalFont))
        
        document.close()
        return out.toByteArray()
    }
}
```

#### 2. AnalysisController: 다운로드 엔드포인트
생성된 PDF를 클라이언트에 전달하기 위해 `ResponseEntity`와 적절한 `HTTP Headers`를 설정했다.

```kotlin
@GetMapping("/{id}/export")
fun exportReport(@PathVariable id: Long): ResponseEntity<ByteArray> {
    val request = repository.findById(id).orElseThrow { RuntimeException("Request not found") }
    val pdf = pdfService.generateAnalysisReport(request)
    
    val headers = HttpHeaders()
    headers.add("Content-Disposition", "attachment; filename=analysis_report_${id}.pdf")
    
    return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdf)
}
```

### 프론트엔드 연동

사용자 경험을 위해 분석이 완료된 상태에서만 'PDF 리포트 다운로드' 버튼이 활성화되도록 구현했다. `fetch`를 통해 받은 블롭(Blob) 데이터를 브라우저에서 파일로 자동 다운로드하는 로직을 React 컴포넌트에 통합했다.

### 개인적인 생각

단순한 데이터 조회를 넘어 문서 형태의 결과물을 제공함으로써, 서비스의 신뢰도가 한 단계 격상되었다고 본다. 특히 Tess4J를 통한 OCR 기능과 연계되어, "이미지를 업로드하면 그 안의 글자를 추출해 정식 PDF 리포트로 만들어주는" 일관된 사용자 흐름을 완성했다는 점이 고무적이다.

앞으로는 PDF 내에 그래프나 분석 통계를 추가해 더욱 풍성한 리포트를 만들 수 있도록 확장할 예정이다.
