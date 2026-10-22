package com.li.campuslife.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.li.campuslife.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    /**
     * 图片保存目录，默认 ./uploads，可用环境变量 CAMPUS_UPLOAD_DIR 或配置项 campus.upload.dir 覆盖。
     * 需要前端直接访问时，把它指向静态资源目录（例如 nginx 站点的 imgs）。
     */
    @Value("${campus.upload.dir:./uploads}")
    private String uploadDir;

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            String originalFilename = image.getOriginalFilename();
            String fileName = createNewFileName(originalFilename);
            File target = new File(uploadDir, fileName);
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("无法创建目录：" + parent);
            }
            image.transferTo(target);
            log.debug("文件上传成功，{}", target.getAbsolutePath());
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        // 前端传的是 /imgs/blogs/...，去掉 /imgs 才是磁盘上的相对路径
        String relative = filename.startsWith("/imgs") ? filename.substring("/imgs".length()) : filename;
        if (!relative.startsWith("/blogs/")) {
            return Result.fail("错误的文件名称");
        }
        File file = new File(uploadDir, relative);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        if (!StrUtil.containsAnyIgnoreCase(suffix, "png", "jpg", "jpeg", "gif", "webp")) {
            throw new IllegalArgumentException("只支持 png/jpg/jpeg/gif/webp 图片");
        }
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
