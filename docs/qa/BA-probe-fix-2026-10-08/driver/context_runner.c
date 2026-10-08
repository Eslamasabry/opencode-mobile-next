#include <dlfcn.h>
#include <unistd.h>
#include <stdio.h>
#include <dirent.h>
#include <stdlib.h>
#include <grp.h>
int main(int argc, char **argv) {
 if(argc<5)return 64;
 unsigned int uid=(unsigned int)strtoul(argv[1],NULL,10);
 if(uid==0 || setgroups(0,NULL)!=0 || setgid(uid)!=0 || setuid(uid)!=0){puts("PROBE_DIAGNOSTIC=uidUnavailable");return 0;}
 void *lib=dlopen("libselinux.so",RTLD_NOW);
 int (*setcon)(const char*)=lib?dlsym(lib,"setcon"):NULL;
 if(!setcon||setcon(argv[2])!=0){puts("PROBE_DIAGNOSTIC=contextUnavailable");return 0;}
 DIR *root=opendir("/proc"), *tasks=opendir("/proc/self/task");
 printf("PROBE_ROOTPROC=%s\n",root?"readable":"unreadable"); if(root)closedir(root);
 printf("PROBE_SELFTASK=%s\n",tasks?"readable":"unreadable");if(tasks)closedir(tasks);
 char path[128];snprintf(path,sizeof(path),"/proc/self/task/%d/children",getpid());
 FILE *children=fopen(path,"r");printf("PROBE_CHILDREN=%s\n",children?"readable":"unreadable");if(children)fclose(children);
 fflush(stdout);
 execv(argv[3],argv+3);
 puts("PROBE_DIAGNOSTIC=execUnavailable");return 0;
}
