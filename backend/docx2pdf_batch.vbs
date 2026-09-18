' dist/docx2pdf_batch.vbs
' 배치 PDF 변환 스크립트 - Word를 1번만 실행하여 N개의 DOCX를 PDF로 일괄 변환합니다.
'
' 사용법: cscript docx2pdf_batch.vbs <manifest_file>
' 매니페스트 파일 형식: 줄마다 "입력파일경로[탭]출력파일경로" (UTF-8 인코딩)

If WScript.Arguments.Count < 1 Then
    WScript.Echo "Usage: cscript docx2pdf_batch.vbs <manifest_file>"
    WScript.Quit 1
End If

Dim manifestPath
manifestPath = WScript.Arguments(0)

' UTF-8 매니페스트 파일 읽기 (ADODB.Stream 사용 - 한글 경로 안전 처리)
Dim adoStream
Set adoStream = CreateObject("ADODB.Stream")
adoStream.Charset = "UTF-8"
adoStream.Open
adoStream.LoadFromFile manifestPath
Dim fileContent
fileContent = adoStream.ReadText()
adoStream.Close
Set adoStream = Nothing

' 줄 단위로 분리
Dim lines
lines = Split(fileContent, Chr(10))

' ── Word를 단 1번만 실행 ──────────────────────────────────────────────────────
Dim wordApp
On Error Resume Next
Set wordApp = CreateObject("Word.Application")
If Err.Number <> 0 Then
    WScript.Echo "Word 실행 실패: " & Err.Description
    WScript.Quit 2
End If
On Error GoTo 0

wordApp.Visible = False
wordApp.DisplayAlerts = 0

Dim errorCount
errorCount = 0

' ── 각 줄(파일 쌍)을 순서대로 변환 ──────────────────────────────────────────
Dim i
For i = 0 To UBound(lines)
    Dim rawLine
    rawLine = Trim(lines(i))

    ' 빈 줄 건너뜀
    If rawLine <> "" Then
        ' Tab 으로 입력/출력 경로 분리
        Dim parts
        parts = Split(rawLine, Chr(9))

        If UBound(parts) >= 1 Then
            ' CR 문자 제거 (Windows 줄바꿈 \r\n 처리)
            Dim docxPath, pdfPath
            docxPath = Replace(Trim(parts(0)), Chr(13), "")
            pdfPath  = Replace(Trim(parts(1)), Chr(13), "")

            On Error Resume Next

            ' 문서 열기 (읽기 전용)
            Dim doc
            Set doc = wordApp.Documents.Open(docxPath, False, True, False)
            If Err.Number <> 0 Then
                WScript.Echo "Error opening [" & docxPath & "]: " & Err.Description
                Err.Clear
                errorCount = errorCount + 1
            Else
                ' PDF로 내보내기 (17 = wdExportFormatPDF)
                doc.ExportAsFixedFormat pdfPath, 17, False, 0, 0, 1, 1, 0, True, True, 0, True, True, False
                If Err.Number <> 0 Then
                    WScript.Echo "Error converting [" & docxPath & "]: " & Err.Description
                    Err.Clear
                    errorCount = errorCount + 1
                End If
                doc.Close False
                Set doc = Nothing
            End If

            On Error GoTo 0
        End If
    End If
Next

' ── Word 종료 ─────────────────────────────────────────────────────────────────
wordApp.Quit
Set wordApp = Nothing

If errorCount > 0 Then
    WScript.Echo errorCount & "개 파일 변환 실패"
    WScript.Quit 1
End If

WScript.Quit 0
