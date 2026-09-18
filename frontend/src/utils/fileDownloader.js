// frontend/src/utils/fileDownloader.js
const DOWNLOAD_MODE_INDIVIDUAL = 'INDIVIDUAL';

export async function downloadResultFile(jobId, certificateNo, mode) {
    const fileResponse = await fetch(`/api/documents/certificates/pdf/download/${jobId}`);
    if (!fileResponse.ok) {
        throw new Error('파일 다운로드에 실패했습니다.');
    }

    let downloadFilename = mode === DOWNLOAD_MODE_INDIVIDUAL ? `${certificateNo}.zip` : `${certificateNo}.pdf`;
    const contentDisposition = fileResponse.headers.get('content-disposition');
    if (contentDisposition) {
        const filenameMatch = contentDisposition.match(/filename="?([^";]+)"?/);
        if (filenameMatch && filenameMatch[1]) {
            downloadFilename = filenameMatch[1];
        }
    }

    const blob = await fileResponse.blob();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = downloadFilename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
}
