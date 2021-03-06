package com.zgw.qgb.delete;

import android.os.Environment;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.zgw.qgb.App;
import com.zgw.qgb.R;
import com.zgw.qgb.helper.utils.FileUtils;
import com.zgw.qgb.helper.utils.NetUtils;
import com.zgw.qgb.net.RetrofitProvider;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public final class Rxdownload {

    private Rxdownload() {
        throw new AssertionError("No instances.");
    }

    private interface DownLoadService {
        @Streaming
        @GET
        Single<ResponseBody> download(@Url String url);


        //downParam下载参数，传下载区间使用
        //url 下载链接

        @Streaming
        @GET
        Single<ResponseBody> download(@Header("RANGE") String downParam, @Url String url);

    }

    public static Single<File> download(String mDownloadUrl) {
        return download(mDownloadUrl, null, null);
    }

    public static Single<File> download(String mDownloadUrl, String filePath, String fileName) {
        File file = getFile(mDownloadUrl, filePath, fileName);
        long start = file.length();
        String range = String.format(App.getLocale(), "bytes=%s-%s",start+"","");

        return RetrofitProvider.getService(DownLoadService.class)
                .download(range, mDownloadUrl)
                .subscribeOn(Schedulers.io())//请求网络 在调度者的io线程
                .doOnSubscribe(disposable -> checkNet())
                .observeOn(Schedulers.io()) //指定线程保存文件
                .map(responseBody -> saveFile(responseBody,file));
    }



    @NonNull
    private static File getFile(String mDownloadUrl, String filePath, String fileName) {
        filePath = TextUtils.isEmpty(filePath)
                ? Environment.getExternalStorageDirectory() + File.separator + App.getContext().getString(R.string.app_name)
                :filePath;
        fileName = TextUtils.isEmpty(fileName)
                ?new File(mDownloadUrl).getName()
                :fileName;

        File fileParent = new File(filePath);
        if(!fileParent.exists()){
            fileParent.mkdirs();
        }
        File file = new File(fileParent, fileName );
        if(!file.exists()){
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }


    private static void checkNet() {
        if (!NetUtils.isConnected(App.getContext())) {
            Toast.makeText(App.getContext(), App.getContext().getText(R.string.please_check_network), Toast.LENGTH_SHORT).show();
        }
    }

    private static File saveFile(ResponseBody responseBody, File file) {

        FileUtils.writeFileFromIS(file,responseBody.byteStream(),true);
        return file;
    }



    public static void head(String mDownloadUrl, Callback callback) {
        Request request = new Request.Builder()
                .url(mDownloadUrl)
                .method("HEAD", null).build();
        RetrofitProvider.provideOkHttp().newCall(request).enqueue(callback);
    }

    public static Single<File> downloadBigFile(String mDownloadUrl, @NonNull @IntRange(from = 0,to = 5) final int threadCount ) {
        return downloadBigFile(mDownloadUrl, threadCount, null, null);
    }

    public static Single<File> downloadBigFile(String mDownloadUrl, @NonNull final int threadCount, String filePath, String fileName) {
        filePath = TextUtils.isEmpty(filePath)
                ? Environment.getExternalStorageDirectory() + File.separator + App.getContext().getString(R.string.app_name) + File.separator
                : filePath;
        fileName = TextUtils.isEmpty(fileName)
                ? new File(mDownloadUrl).getName()
                : fileName;

        String finalFilePath = filePath;
        String finalFileName = fileName;


        return RetrofitProvider.getService(DownLoadService.class)
                .download(mDownloadUrl)
                .subscribeOn(Schedulers.io())//请求网络 在调度者的io线程
                .doOnSubscribe(disposable -> checkNet())
                .observeOn(Schedulers.io()) //指定线程保存文件
                .map(responseBody -> fileSeparateDownload(responseBody, finalFilePath, finalFileName, threadCount, mDownloadUrl)) ;

    }

    private static File fileSeparateDownload(ResponseBody responseBody, String finalFilePath, String finalFileName, @NonNull int threadCount, String mDownloadUrl) {
        long contentLength = responseBody.contentLength();
        responseBody.close();

        RandomAccessFile tmpAccessFile = null;
        File mTmpFile = null;
        File fileParent = new File(finalFilePath);
        if (!fileParent.exists()) {
            fileParent.mkdirs();
        }
        mTmpFile = new File(fileParent, finalFileName);
        if (!mTmpFile.exists()) {
            try {
                mTmpFile.createNewFile();
                tmpAccessFile = new RandomAccessFile(mTmpFile, "rw");
                fileSeparateDownload(contentLength, tmpAccessFile, threadCount, mDownloadUrl);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return mTmpFile;//下载完毕后，重命名目标文件名;
    }

    private static void getRemoteFileAndSeparate(String mDownloadUrl, @NonNull int threadCount, String finalFilePath, String finalFileName, RandomAccessFile finalTmpAccessFile) {
        head(mDownloadUrl, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                long contentLength = Long.valueOf(response.header("Content-Length"));

                if (contentLength > 0) {
                    fileSeparateDownload(contentLength, finalTmpAccessFile, threadCount, mDownloadUrl);
                } else {
                    download(mDownloadUrl, finalFilePath, finalFileName);
                }
            }
        });
    }

    private static void fileSeparateDownload(long contentLength, RandomAccessFile finalTmpAccessFile, @NonNull int threadCount, String mDownloadUrl) throws IOException {
        finalTmpAccessFile.setLength(contentLength);
        long onceLenth = contentLength / threadCount;

        DownLoadService service = RetrofitProvider.getService(DownLoadService.class);

        List<Observable<ResponseBodyAndPoint>> observableList = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            long end = i == threadCount - 1 ? contentLength : onceLenth * (i + 1);
            long start = onceLenth * i;
            Observable<ResponseBodyAndPoint> observe = (service.download("bytes=" + start + "-" + end, mDownloadUrl))
                    .toObservable()
                    .map(responseBody1 -> new ResponseBodyAndPoint(responseBody1, start, end));
            observableList.add(observe);
            Log.d("Rxdownload", "threadCount  " + threadCount + "start  " + start + "end  " + end);
        }

        Observable.merge(observableList)
                .subscribeOn(Schedulers.io())//请求网络 在调度者的io线程
                .observeOn(Schedulers.io())
                .doOnNext(responseBodyAndPoint -> writeStream2RandomAccessFile(responseBodyAndPoint, finalTmpAccessFile))
        .subscribe() ;

    }


    private static RandomAccessFile writeStream2RandomAccessFile(ResponseBodyAndPoint responseBodyAndPoint, RandomAccessFile tmpAccessFile) throws IOException {
        tmpAccessFile.seek(responseBodyAndPoint.startPoint);
        Log.d("Rxdownload", responseBodyAndPoint.startPoint + ",,," + responseBodyAndPoint.endPoint);
        byte[] buffer = new byte[1024 << 2];
        InputStream is = responseBodyAndPoint.responseBody.byteStream();// 获取流
        int length;
        while ((length = is.read(buffer)) > 0) {//读取流
            tmpAccessFile.write(buffer, 0, length);
        }


        return tmpAccessFile;
    }



    private static class ResponseBodyAndPoint {
        ResponseBody responseBody;
        long startPoint;
        long endPoint;

        public ResponseBodyAndPoint(ResponseBody responseBody, long startPoint, long endPoint) {
            this.responseBody = responseBody;
            this.startPoint = startPoint;
            this.endPoint = endPoint;
        }
    }
}
