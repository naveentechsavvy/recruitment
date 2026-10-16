import { RESUME_MAX_BYTES, resumeFileError } from './upload.config';

describe('resumeFileError', () => {
  const file = (name: string, size: number) => new File([new Uint8Array(size)], name);

  it('asks for a file when none is chosen', () => {
    expect(resumeFileError(null)).toContain('choose');
  });

  it('rejects an empty file', () => {
    expect(resumeFileError(new File([], 'cv.pdf'))).toContain('empty');
  });

  it('rejects the wrong file type', () => {
    expect(resumeFileError(file('notes.txt', 10))).toContain('PDF, DOC or DOCX');
  });

  it('rejects a file over the limit', () => {
    expect(resumeFileError(file('big.pdf', RESUME_MAX_BYTES + 1))).toContain('MB or smaller');
  });

  it('accepts pdf, doc and docx', () => {
    expect(resumeFileError(file('a.PDF', 10))).toBeNull();
    expect(resumeFileError(file('a.doc', 10))).toBeNull();
    expect(resumeFileError(file('a.docx', 10))).toBeNull();
  });
});
