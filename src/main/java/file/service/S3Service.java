package file.service;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;

import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;


import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class S3Service {
	
	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;
	
    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;
    
    private final String DIR_NAME = "s3_data";
    
    // 파일 업로드
	@Transactional
	public void uploadS3File(MultipartFile file) throws Exception {
		
		// C:/cloud/98.data/s3_data에 파일 저장 -> S3 전송 및 저장 (putObject)
		if(file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		
		//DB 저장
		String savePath = "/home/ubuntu/" + DIR_NAME; // 슬래시 하나로 통일해도 됨

		String attachmentOriginalFilename = file.getOriginalFilename();
		UUID uuid = UUID.randomUUID();
		String attachmentFileName = uuid.toString() + "_" + file.getOriginalFilename();
		Long attachmentFileSize = file.getSize();
		
		
		// Enrity 변환
		AttachmentFile attachmentfile = AttachmentFile.builder()
													.attachmentFileName(attachmentFileName)
													.attachmentOriginalFileName(attachmentOriginalFilename)
													.filePath(savePath)
													.attachmentFileSize(attachmentFileSize)
													.build();
		Long fileNo = fileRepository.save(attachmentfile).getAttachmentFileNo();
		
		//s3에 물리적으로 저장
		if(fileNo != null) {
			//임시 파일 저장 -> 이거 꼭 필요 그래서 밑에서 삭제 시켜줘야함 
			File uploadFile = new File(attachmentfile.getFilePath() + "/" + attachmentFileName);
			file.transferTo(uploadFile);
			
			//s3 파일 전송
			//파라미터 전달 시 bucket : 버킷
			//key : 객체의 저장 경로 + 객체의 이름
			//file : 물리적인 리소스 (위에 만든거)
			
			//리눅스는 / 하나만, 윈도우는 //
			String key = DIR_NAME + "/" + uploadFile.getName(); 
			amazonS3.putObject(bucketName, key, uploadFile);
			
			//임시 파일 삭제 
			if(uploadFile.exists()) {
				uploadFile.delete();
			}
		}
		
	}
	
	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo){
		AttachmentFile attachmentFile = null;
		Resource resource = null;
		
		// DB에서 파일 검색 -> S3의 파일 가져오기 (getObject) -> 전달
		attachmentFile = fileRepository.findById(fileNo)
										.orElseThrow(()-> new NoSuchElementException("파일 없음"));
		String key = DIR_NAME + "/" + attachmentFile.getAttachmentFileName();
		S3Object s3Object=  amazonS3.getObject(bucketName, key);
		S3ObjectInputStream s3ois = s3Object.getObjectContent();
		resource = new InputStreamResource(s3ois);
		
		HttpHeaders headers = new HttpHeaders();

		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition(ContentDisposition
										.builder("attachment")
										.filename(attachmentFile.getAttachmentFileName())
										.build());
		
		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);

		
	}
	
}
