package com.android.imusic.music.activity;

import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import com.android.imusic.R;
import com.android.imusic.base.BaseActivity;
import com.android.imusic.music.adapter.MusicCommenListAdapter;
import com.android.imusic.music.bean.MusicDetails;
import com.android.imusic.music.dialog.MusicMusicDetailsDialog;
import com.android.imusic.music.manager.SqlLiteCacheManager;
import com.android.imusic.music.ui.contract.MusicHistroyContract;
import com.android.imusic.music.ui.presenter.MusicHistroyPersenter;
import com.music.player.lib.bean.BaseAudioInfo;
import com.music.player.lib.bean.MusicStatus;
import com.music.player.lib.constants.MusicConstants;
import com.music.player.lib.listener.MusicOnItemClickListener;
import com.music.player.lib.manager.MusicPlayerManager;
import com.music.player.lib.manager.MusicSubjectObservable;
import com.music.player.lib.util.MusicUtils;
import com.music.player.lib.view.MusicCommentTitleView;

import java.util.List;
import java.util.Observable;
import java.util.Observer;

/**
 * TinyHung@Outlook.com
 * 2019/3/25
 * Music Collect
 */

public class MusicCollectActivity extends BaseActivity<MusicHistroyPersenter> implements
        MusicOnItemClickListener, Observer,MusicHistroyContract.View {

    private MusicCommenListAdapter mAdapter;
    private LinearLayoutManager mLayoutManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setWindowEnable(true);
        setContentView(R.layout.music_activity_music_list);
        MusicCommentTitleView titleView = (MusicCommentTitleView) findViewById(R.id.title_view);
        titleView.setTitle("我的收藏");
        titleView.setOnTitleClickListener(new MusicCommentTitleView.OnTitleClickListener() {
            @Override
            public void onBack(View view) {
                finish();
            }
        });
        ((SwipeRefreshLayout) findViewById(R.id.swipre_layout)).setEnabled(false);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mLayoutManager = new LinearLayoutManager(this,LinearLayoutManager.VERTICAL,false);
        recyclerView.setLayoutManager(mLayoutManager);
        mAdapter = new MusicCommenListAdapter(MusicCollectActivity.this,null,this);
        recyclerView.setAdapter(mAdapter);
        MusicPlayerManager.getInstance().addObservable(this);
    }

    @Override
    protected MusicHistroyPersenter createPresenter() {
        return new MusicHistroyPersenter();
    }

    /**
     * @param view
     * @param position
     * @param musicID >0 为单击事件，反之为菜单点击事件
     */
    @Override
    public void onItemClick(View view, final int position, long musicID) {
        if(null!=view.getTag()){
            final BaseAudioInfo audioInfo= (BaseAudioInfo) view.getTag();
            if(musicID>0){
                long currentPlayerID = MusicPlayerManager.getInstance().getCurrentPlayerID();
                if(currentPlayerID>0&&currentPlayerID==audioInfo.getAudioId()){
                    //重复点击，打开播放器
                    startToMusicPlayer(currentPlayerID);
                    return;
                }
                //重新确定选中的对象
                mAdapter.notifyDataSetChanged(position);
                MusicPlayerManager.getInstance().setPlayingChannel(MusicConstants.CHANNEL_COLLECT);
                //开始播放
                MusicPlayerManager.getInstance().startPlayMusic(mAdapter.getData(),position);
                //如果悬浮窗权限未给定
                createMiniJukeboxWindow();
            }else{
                //Menu
                MusicMusicDetailsDialog.getInstance(MusicCollectActivity.this,audioInfo,
                        MusicMusicDetailsDialog.DialogScene.SCENE_COLLECT)
                        .setMusicOnItemClickListener(new MusicOnItemClickListener() {
                            /**
                             * @param view
                             * @param itemId 参考 MusicDetails 定义
                             * @param musicID
                             */
                            @Override
                            public void onItemClick(View view, int itemId, long musicID) {
                                onMusicMenuClick(position,itemId,audioInfo);
                            }
                        }).show();
            }
        }
    }

    /**
     * 菜单处理
     * @param position
     * @param itemId
     * @param audioInfo
     */
    @Override
    protected void onMusicMenuClick(int position, int itemId, final BaseAudioInfo audioInfo) {
        super.onMusicMenuClick(position, itemId, audioInfo);
        if(itemId== MusicDetails.ITEM_ID_DETELE){
            new android.support.v7.app.AlertDialog.Builder(MusicCollectActivity.this)
                    .setTitle("删除提示")
                    .setMessage("确定要从收藏列表中删除这首歌吗？")
                    .setNegativeButton("取消",null)
                    .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            boolean flag = SqlLiteCacheManager.getInstance().deteleCollectByID(audioInfo.getAudioId());
                            if(flag){
                                MusicPlayerManager.getInstance().observerUpdata(new MusicStatus());
                                Toast.makeText(MusicCollectActivity.this,"已删除",Toast.LENGTH_SHORT).show();
                                if(null!=mPresenter){
                                    mPresenter.getCollectAudios();
                                }
                            }
                        }
                    }).setCancelable(false).show();
        }
    }

    /**
     * 显示用户收藏列表
     * @param data 收藏、历史播放 记录
     */
    @Override
    public void showAudios(List<BaseAudioInfo> data) {
        if(null!=mAdapter){
            mAdapter.setNewData(data);
        }
        MusicPlayerManager.getInstance().observerUpdata(new MusicStatus());
    }

    @Override
    public void showError(int code, String errorMsg) {
        super.showError(code, errorMsg);
        if(null!=mAdapter){
            mAdapter.setNewData(null);
        }
    }

    @Override
    public void update(Observable o, final Object arg) {
        if(null!=mAdapter&&o instanceof MusicSubjectObservable && null!=arg && arg instanceof MusicStatus){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    MusicStatus musicStatus= (MusicStatus) arg;
                    if(MusicStatus.PLAYER_STATUS_DESTROY==musicStatus.getPlayerStatus()
                            ||MusicStatus.PLAYER_STATUS_STOP==musicStatus.getPlayerStatus()){
                        //播放器被销毁或停止
                        if(null!=mAdapter.getData()&&mAdapter.getData().size()>mAdapter.getCurrentPosition()){
                            mAdapter.getData().get(mAdapter.getCurrentPosition()).setSelected(false);
                            mAdapter.notifyDataSetChanged();
                        }
                    }else{
                        //播放器对象发生了变化
                        mAdapter.notifyDataSetChanged();
                        int position = MusicUtils.getInstance().getCurrentPlayIndexInThis(mAdapter.getData(),
                                MusicPlayerManager.getInstance().getCurrentPlayerID());
                        mAdapter.setCurrentPosition(position);
                    }
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(null!=mAdapter&&null!=mPresenter){
            mPresenter.getCollectAudios();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(null!=mAdapter){
            mAdapter.onDestroy();
            mAdapter=null;
        }
        MusicPlayerManager.getInstance().removeObserver(this);
    }
}