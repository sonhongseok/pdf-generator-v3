If WScript.Arguments.Count < 2 Then
    WScript.Echo "Usage: cscript docx2pdf.vbs <input.docx> <output.pdf>"
    WScript.Quit 1
End If

Dim docxPath, pdfPath
docxPath = WScript.Arguments(0)
pdfPath = WScript.Arguments(1)

On Error Resume Next
Set wordApp = CreateObject("Word.Application")
' 숨김 처리 및 모든 경고창 무시
wordApp.Visible = False
wordApp.DisplayAlerts = 0

' 문서 읽기 전용으로 열기
Set doc = wordApp.Documents.Open(docxPath, False, True, False)
If Err.Number <> 0 Then
    WScript.Echo "Error opening document: " & Err.Description
    wordApp.Quit
    WScript.Quit 2
End If

' 확실한 PDF 내보내기 명령 (17 = wdExportFormatPDF)
doc.ExportAsFixedFormat pdfPath, 17, False, 0, 0, 1, 1, 0, True, True, 0, True, True, False
If Err.Number <> 0 Then
    WScript.Echo "Error exporting PDF: " & Err.Description
    doc.Close False
    wordApp.Quit
    WScript.Quit 3
End If

doc.Close False
wordApp.Quit
WScript.Quit 0
