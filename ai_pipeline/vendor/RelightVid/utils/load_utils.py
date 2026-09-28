import torch
from omegaconf import OmegaConf
from pytorch_lightning.loggers import WandbLogger
from utils.model_utils import instantiate_from_config, get_obj_from_str

from diffusers import AutoencoderKL
import os
import json


def get_text_model(args):
    sd_path = args.diffusion.params.sd_path
    args.text_model['params']['sd_path'] = sd_path
    text_model = instantiate_from_config(args.text_model)
    return text_model

def get_vae(args):
    sd_path = args.diffusion.params.sd_path
    vae = AutoencoderKL.from_pretrained(sd_path, subfolder="vae")
    return vae

def get_sd_models(args):
    model_dict = {}
    unet = instantiate_from_config(args.unet)
    model_dict['unet'] = unet

    vae = get_vae(args) 
    model_dict['vae'] = vae

    text_model = get_text_model(args)
    model_dict['text_model'] = text_model

    if args.get('ctrlnet'): 
        ctrlnet = instantiate_from_config(args.ctrlnet)
        model_dict['ctrlnet'] = ctrlnet

    return model_dict


def get_relvid_model(diffusion_configs, log_args={}, **models):
    diffusion_model_class = diffusion_configs['target']
    diffusion_args = diffusion_configs['params']
    DDPM_model = get_obj_from_str(diffusion_model_class) 
    ddpm_model = DDPM_model(
        log_args=log_args,
        **models,
        **diffusion_args
    )
    return ddpm_model


def get_logger(args):
    wandb_logger = WandbLogger(
        project=args["expt_name"],
    )
    return wandb_logger

def get_callbacks(args, wandb_logger):
    callbacks = []
    for callback in args['callbacks']:
        if callback.get('require_wandb', False):
            # we need to pass wandb logger to the callback
            callback_obj = get_obj_from_str(callback.target)
            callbacks.append(
                callback_obj(wandb_logger=wandb_logger, **callback.params)
            )
        else:
            callbacks.append(
                instantiate_from_config(callback)
            )
    return callbacks

def get_dataset(args):
    from torch.utils.data import DataLoader
    data_args = args['data']
    train_set = instantiate_from_config(data_args['train'])
    val_set = instantiate_from_config(data_args['val'])
    train_loader = DataLoader(
        train_set, batch_size=data_args['batch_size'], shuffle=True,
        num_workers=4*len(args['trainer_args']['devices']), pin_memory=True
    )
    val_loader = DataLoader(
        val_set, batch_size=data_args['val_batch_size'],
        num_workers=len(args['trainer_args']['devices']), pin_memory=True
    ) 
    return train_loader, val_loader, train_set, val_set


def init_create_model(config_path):
    device = 'cuda' if torch.cuda.is_available() else 'cpu'
    conf = OmegaConf.load(config_path)
    models = get_sd_models(conf)
    ddpm = get_relvid_model(conf['diffusion'], log_args=conf, **models)
    ddpm = ddpm.to(device)
    return ddpm
